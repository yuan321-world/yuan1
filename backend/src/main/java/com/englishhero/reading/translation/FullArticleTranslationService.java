package com.englishhero.reading.translation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.ArticleContent;
import com.englishhero.reading.EditorialReadingCatalog;
import com.englishhero.reading.ReadingDtos.ArticleVersionDto;
import com.englishhero.reading.ReadingService;
import com.englishhero.web.BusinessException;
import static com.englishhero.reading.translation.FullArticleTranslationDtos.*;

@Service
public class FullArticleTranslationService {
    private final ReadingService reading;
    private final EditorialReadingCatalog catalog;
    private final FullArticleTranslationJobs jobs;
    private final BaiduTranslationProperties baidu;
    private final BaiduTranslationClient client;
    private final FullArticleTranslationProperties properties;
    private final TransactionTemplate transaction;

    public FullArticleTranslationService(ReadingService reading, EditorialReadingCatalog catalog,
            FullArticleTranslationJobs jobs, BaiduTranslationProperties baidu, BaiduTranslationClient client,
            FullArticleTranslationProperties properties, org.springframework.transaction.PlatformTransactionManager manager) {
        this.reading = reading;
        this.catalog = catalog;
        this.jobs = jobs;
        this.baidu = baidu;
        this.client = client;
        this.properties = properties;
        this.transaction = new TransactionTemplate(manager);
    }

    public Response get(SessionPrincipal actor, String articleId, String versionId) {
        var version = reading.version(actor, articleId, versionId).version();
        return snapshot(version);
    }

    public Response enqueue(SessionPrincipal actor, String articleId, String versionId) {
        var version = reading.version(actor, articleId, versionId).version();
        if (!catalog.translation(version).available() && !configured()) {
            throw new BusinessException(503, "FULL_TRANSLATION_UNAVAILABLE", "全文机器翻译暂不可用");
        }
        if (!catalog.translation(version).available()) {
            jobs.enqueue(version, contentHash(version));
        }
        return snapshot(version);
    }

    /** Returns the project-edited reference where one exists, then any complete or partial machine cache. */
    public EditorialReadingCatalog.Translation translationFor(ArticleVersionDto version) {
        return snapshot(version).translation();
    }

    public int seedPublished() {
        if (!configured()) return 0;
        return transaction.execute(status -> {
            if (!jobs.beginSeed()) return 0;
            int enqueued = 0;
            for (var row : jobs.publishedVersions()) {
                var version = reading.version(null, Long.toString(row.articleId()), Long.toString(row.versionId())).version();
                if (catalog.translation(version).available()) continue;
                enqueued += jobs.enqueue(version, contentHash(version));
            }
            return enqueued;
        });
    }

    /** Processes one chunk with the external request outside the queue's database transactions. */
    public boolean workOnce() {
        if (!configured()) return false;
        jobs.recoverExpired();
        Optional<ChunkJob> claimed = jobs.claim();
        if (claimed.isEmpty()) return false;
        var job = claimed.get();
        try {
            jobs.succeed(job, client.translate(job.text()));
        } catch (BaiduTranslationClient.Failure failure) {
            jobs.fail(job, failure.code, failure.definitelyUnsent);
        } catch (RuntimeException failure) {
            jobs.fail(job, "OUTCOME_UNKNOWN", false);
        }
        return true;
    }

    private Response snapshot(ArticleVersionDto version) {
        var editorial = catalog.translation(version);
        int total = version.content().paragraphs().size();
        if (editorial.available()) {
            return new Response("SUCCEEDED", total, total, configured(), editorial, null);
        }

        String hash = contentHash(version);
        List<ChunkRow> rows = jobs.rows(Long.parseLong(version.id()), hash);
        var empty = new EditorialReadingCatalog.Translation(false, "机器参考译文", List.of(), List.of());
        if (rows.isEmpty()) return new Response("MISSING", 0, total, configured(), empty, null);

        Map<String, List<ChunkRow>> byParagraph = new LinkedHashMap<>();
        rows.forEach(row -> byParagraph.computeIfAbsent(row.paragraphKey(), ignored -> new ArrayList<>()).add(row));
        var paragraphs = new ArrayList<EditorialReadingCatalog.ParagraphTranslation>();
        int completed = 0;
        String errorCode = null;
        boolean queued = false;
        boolean running = false;
        for (var row : rows) {
            if ("QUEUED".equals(row.status())) queued = true;
            if ("RUNNING".equals(row.status())) running = true;
            if (errorCode == null && "FAILED".equals(row.status())) errorCode = row.errorCode();
        }
        for (var paragraph : version.content().paragraphs()) {
            var chunks = byParagraph.get(paragraph.key());
            if (chunks == null || chunks.isEmpty() || chunks.stream().anyMatch(row -> !"SUCCEEDED".equals(row.status()))) continue;
            chunks.sort(java.util.Comparator.comparingInt(ChunkRow::chunkIndex));
            String text = chunks.stream().map(row -> row.separatorBefore() + Objects.toString(row.translatedText(), "").strip())
                    .collect(java.util.stream.Collectors.joining());
            paragraphs.add(new EditorialReadingCatalog.ParagraphTranslation(paragraph.key(), text));
            completed++;
        }

        String state;
        if (completed == total) state = "SUCCEEDED";
        else if (running) state = "RUNNING";
        else if (queued) state = "QUEUED";
        else state = "FAILED";
        String error = "FAILED".equals(state) ? friendlyError(errorCode) : null;
        var translation = new EditorialReadingCatalog.Translation(completed == total, "机器参考译文",
                List.copyOf(paragraphs), List.of());
        return new Response(state, completed, total, configured(), translation, error);
    }

    private boolean configured() { return baidu.ready() && properties.isEnabled(); }

    private static String friendlyError(String code) {
        if ("BAIDU_CHARACTER_QUOTA".equals(code)) return "本月翻译字符额度已用完，请下月再试";
        if ("BAIDU_54003".equals(code) || "BAIDU_54005".equals(code)) return "翻译服务繁忙，请稍后重试";
        if ("OUTCOME_UNKNOWN".equals(code) || "BAIDU_OUTCOME_UNKNOWN".equals(code)) return "翻译结果暂未确认，请稍后查看并重试";
        return "翻译失败，请稍后重试";
    }

    private String contentHash(ArticleVersionDto version) {
        StringBuilder exact = new StringBuilder().append(version.content().schemaVersion()).append('\n');
        for (var paragraph : version.content().paragraphs()) {
            append(exact, paragraph.key());
            for (var sentence : paragraph.sentences()) {
                append(exact, sentence.key());
                append(exact, sentence.text());
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(exact.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Document hash unavailable");
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('\n');
    }
}
