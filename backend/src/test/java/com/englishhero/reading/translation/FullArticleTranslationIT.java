package com.englishhero.reading.translation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class FullArticleTranslationIT extends SelectionTranslationHttpITSupport {
    @Autowired FullArticleTranslationService fullTranslations;
    @Autowired FullArticleTranslationProperties fullProperties;
    @Autowired BaiduTranslationProperties baiduProperties;
    @MockitoBean FullArticleTranslationWorker worker;
    @MockitoBean BaiduTranslationClient baidu;
    private boolean previousEnabled;
    private long previousMonthlyLimit;

    @BeforeEach
    void enableManualTestWorker() {
        previousEnabled = fullProperties.isEnabled();
        previousMonthlyLimit = baiduProperties.getMonthlyCharacterLimit();
        fullProperties.setEnabled(true);
    }

    @Test
    void queueIsVersionBoundPubliclyReadableAuthenticatedAndIdempotent() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var other = actor(false);
        var article = published(admin);
        String path = path(article);

        var initial = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(initial.path("status").asText()).isEqualTo("MISSING");
        assertThat(initial.path("configured").asBoolean()).isTrue();
        assertThat(initial.path("translation").path("available").asBoolean()).isFalse();
        assertThat(call(guest(), "POST", path, null).statusCode()).isEqualTo(401);

        var queued = call(learner, "POST", path, null);
        assertThat(queued.statusCode()).isEqualTo(200);
        assertThat(json.readTree(queued.body()).path("status").asText()).isEqualTo("QUEUED");
        call(learner, "POST", path, null);
        long version = Long.parseLong((String) article.get("versionId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_chunk WHERE version_id=?", Integer.class, version)).isEqualTo(1);
        assertThat(json.readTree(call(guest(), "GET", path, null).body()).path("status").asText()).isEqualTo("QUEUED");

        stubTranslation();
        assertThat(fullTranslations.workOnce()).isTrue();
        var completed = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(completed.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(completed.path("completed").asInt()).isEqualTo(1);
        assertThat(completed.path("translation").path("available").asBoolean()).isTrue();
        assertThat(completed.at("/translation/paragraphs/0/key").asText()).isEqualTo("p0001");
        assertThat(completed.path("translation").path("source").asText()).isEqualTo("机器参考译文");
        assertThat(fullTranslations.workOnce()).isFalse();
        verify(baidu).translate(anyString());

        var tools = json.readTree(call(guest(), "GET", "/reading/articles/" + article.get("articleId")
                + "/versions/" + article.get("versionId") + "/tools", null).body());
        assertThat(tools.path("translation").path("available").asBoolean()).isTrue();

        var unrelated = published(admin);
        String mismatched = "/reading/articles/" + unrelated.get("articleId") + "/versions/"
                + article.get("versionId") + "/full-translation";
        assertThat(call(other, "GET", mismatched, null).statusCode()).isEqualTo(404);
    }

    @Test
    void historicalVersionIsVisibleOnlyToAnOwnerAndLongParagraphsExposeOnlyCompleteTranslations() throws Exception {
        var admin = actor(true);
        var owner = actor(false);
        var other = actor(false);
        String body = "word ".repeat(360);
        var draft = new com.englishhero.reading.ReadingDtos.DraftInput("1", "Long full translation " + System.nanoTime(),
                "长篇翻译测试", "A long translation test.", "B1", body, "Test Author", "ORIGINAL", null, "原创测试");
        var created = json.readTree(call(admin, "POST", "/admin/articles", draft).body());
        String articleId = created.path("id").asText();
        String versionId = created.path("draftVersionId").asText();
        call(admin, "POST", "/admin/articles/" + articleId + "/publish",
                Map.of("expectedLockVersion", created.path("lockVersion").asInt()));
        call(owner, "POST", "/reading/articles/" + articleId + "/start", null);
        var article = Map.of("articleId", articleId, "versionId", versionId);
        String path = path(article);

        stubTranslation();
        var queued = json.readTree(call(owner, "POST", path, null).body());
        assertThat(queued.path("status").asText()).isEqualTo("QUEUED");
        long version = Long.parseLong(versionId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_chunk WHERE version_id=?", Integer.class, version)).isEqualTo(2);

        assertThat(fullTranslations.workOnce()).isTrue();
        var partial = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(partial.path("status").asText()).isEqualTo("QUEUED");
        assertThat(partial.path("completed").asInt()).isZero();
        assertThat(partial.path("translation").path("available").asBoolean()).isFalse();
        assertThat(partial.path("translation").path("paragraphs")).isEmpty();
        assertThat(fullTranslations.workOnce()).isTrue();
        var complete = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(complete.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(complete.path("completed").asInt()).isEqualTo(1);

        var current = json.readTree(call(admin, "GET", "/admin/articles/" + articleId, null).body());
        call(admin, "POST", "/admin/articles/" + articleId + "/unpublish",
                Map.of("expectedLockVersion", current.path("lockVersion").asInt()));
        assertThat(call(guest(), "GET", path, null).statusCode()).isEqualTo(404);
        assertThat(call(owner, "GET", path, null).statusCode()).isEqualTo(200);
        assertThat(call(other, "GET", path, null).statusCode()).isEqualTo(404);
    }

    @Test
    void completedChunksReserveTheSharedMonthlyQuotaOnceAndRemainCached() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var article = published(admin);
        String path = path(article);
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Shanghai")).withDayOfMonth(1);
        jdbc.update("INSERT INTO reading_baidu_monthly_quota(month_start) VALUES(?) ON DUPLICATE KEY UPDATE month_start=VALUES(month_start)", month);
        long before = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);

        call(learner, "POST", path, null);
        stubTranslation();
        assertThat(fullTranslations.workOnce()).isTrue();
        long chars = jdbc.queryForObject("""
                SELECT SUM(a.source_characters) FROM reading_full_translation_attempt a
                JOIN reading_full_translation_chunk c ON c.id=a.chunk_id WHERE c.version_id=?
                """, Long.class, Long.parseLong((String) article.get("versionId")));
        long after = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);
        assertThat(after - before).isEqualTo(chars);

        clearInvocations(baidu);
        call(learner, "POST", path, null);
        assertThat(fullTranslations.workOnce()).isFalse();
        verify(baidu, org.mockito.Mockito.never()).translate(anyString());
        assertThat(jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month)).isEqualTo(after);
    }

    @Test
    void disabledTranslationCannotBeQueuedAndReportsThatItIsUnavailable() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var article = published(admin);
        String path = path(article);
        fullProperties.setEnabled(false);

        var current = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(current.path("configured").asBoolean()).isFalse();
        assertThat(call(learner, "POST", path, null).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_chunk WHERE version_id=?", Integer.class,
                Long.parseLong((String) article.get("versionId")))).isZero();
    }

    @Test
    void monthlyQuotaStopsAdditionalChunksAndReturnsAUserSafeError() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var article = publishedLong(admin, "word ".repeat(360));
        String path = path(article);
        long versionId = Long.parseLong((String) article.get("versionId"));
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Shanghai")).withDayOfMonth(1);
        jdbc.update("INSERT INTO reading_baidu_monthly_quota(month_start) VALUES(?) ON DUPLICATE KEY UPDATE month_start=VALUES(month_start)", month);
        long before = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);
        call(learner, "POST", path, null);
        String firstText = jdbc.queryForObject("SELECT source_text FROM reading_full_translation_chunk WHERE version_id=? ORDER BY chunk_index LIMIT 1", String.class, versionId);
        int firstChunk = firstText.codePointCount(0, firstText.length());
        baiduProperties.setMonthlyCharacterLimit(before + firstChunk);
        stubTranslation();

        assertThat(fullTranslations.workOnce()).isTrue();
        assertThat(fullTranslations.workOnce()).isFalse();
        var failed = json.readTree(call(guest(), "GET", path, null).body());
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("error").asText()).contains("额度").doesNotContain("BAIDU");
        assertThat(jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month))
                .isEqualTo(before + firstChunk);
        verify(baidu, times(1)).translate(anyString());
    }

    @Test
    void concurrentClaimsUseTheSharedMonthlyCounterAndKnownUnsentRateLimitsReleaseReservations() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var article = publishedLong(admin, "word ".repeat(360));
        String path = path(article);
        long versionId = Long.parseLong((String) article.get("versionId"));
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Shanghai")).withDayOfMonth(1);
        jdbc.update("INSERT INTO reading_baidu_monthly_quota(month_start) VALUES(?) ON DUPLICATE KEY UPDATE month_start=VALUES(month_start)", month);
        long before = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);
        call(learner, "POST", path, null);
        stubTranslation();

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(fullTranslations::workOnce);
            var second = pool.submit(fullTranslations::workOnce);
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        long attemptChars = jdbc.queryForObject("""
                SELECT SUM(a.source_characters) FROM reading_full_translation_attempt a
                JOIN reading_full_translation_chunk c ON c.id=a.chunk_id WHERE c.version_id=?
                """, Long.class, versionId);
        long after = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);
        assertThat(after - before).isEqualTo(attemptChars);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_chunk WHERE version_id=? AND status='SUCCEEDED'", Integer.class, versionId)).isEqualTo(2);

        var rateLimited = published(admin);
        String ratePath = path(rateLimited);
        var rateQueued = json.readTree(call(learner, "POST", ratePath, null).body());
        assertThat(rateQueued.path("status").asText()).isEqualTo("QUEUED");
        when(baidu.translate(anyString())).thenThrow(new BaiduTranslationClient.Failure("BAIDU_54003", true));
        long beforeRetry = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month);
        for (int i = 0; i < 3; i++) {
            boolean worked = fullTranslations.workOnce();
            var row = jdbc.queryForMap("SELECT status,attempt_count,rate_limit_attempts FROM reading_full_translation_chunk WHERE version_id=?",
                    Long.parseLong((String) rateLimited.get("versionId")));
            assertThat(worked).as("attempt %d, queue=%s, attempts=%s, rateAttempts=%s, enabled=%s, quota=%s/%s",
                    i + 1, row.get("status"), row.get("attempt_count"), row.get("rate_limit_attempts"), fullProperties.isEnabled(),
                    jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month),
                    baiduProperties.getMonthlyCharacterLimit()).isTrue();
            if (i < 2) {
                long rateVersion = Long.parseLong((String) rateLimited.get("versionId"));
                jdbc.update("UPDATE reading_full_translation_chunk SET available_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 10 SECOND) WHERE version_id=?", rateVersion);
                Long delay = jdbc.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND,available_at,UTC_TIMESTAMP(6)) FROM reading_full_translation_chunk WHERE version_id=?", Long.class, rateVersion);
                assertThat(delay).isPositive();
            }
        }
        long rateVersion = Long.parseLong((String) rateLimited.get("versionId"));
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM reading_full_translation_chunk WHERE version_id=?", Integer.class, rateVersion)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_attempt a JOIN reading_full_translation_chunk c ON c.id=a.chunk_id WHERE c.version_id=? AND a.status='RELEASED'", Integer.class, rateVersion)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=?", Long.class, month)).isEqualTo(beforeRetry);
        var failed = json.readTree(call(guest(), "GET", ratePath, null).body());
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("error").asText()).contains("繁忙").doesNotContain("54003");

        jdbc.update("UPDATE reading_full_translation_chunk SET retry_after=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 10 SECOND) WHERE version_id=?", rateVersion);
        var retried = json.readTree(call(learner, "POST", ratePath, null).body());
        assertThat(retried.path("status").asText()).isEqualTo("QUEUED");
        doAnswer(invocation -> new BaiduTranslationClient.Result("译文")).when(baidu).translate(anyString());
        assertThat(fullTranslations.workOnce()).isTrue();
        assertThat(json.readTree(call(guest(), "GET", ratePath, null).body()).path("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void seedSkipsPublishedPracticeOnlyArticles() throws Exception {
        var admin = actor(true);
        var article = published(admin);
        long articleId = Long.parseLong((String) article.get("articleId"));
        long versionId = Long.parseLong((String) article.get("versionId"));
        jdbc.update("UPDATE reading_article SET content_usage='PRACTICE' WHERE id=?", articleId);
        jdbc.update("DELETE FROM reading_full_translation_seed");

        assertThat(fullTranslations.seedPublished()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_full_translation_chunk WHERE version_id=?", Integer.class, versionId)).isZero();
        jdbc.update("DELETE FROM reading_full_translation_seed");
    }

    private void stubTranslation() {
        when(baidu.translate(anyString())).thenAnswer(invocation -> new BaiduTranslationClient.Result("译文"));
    }

    private Map<String, Object> publishedLong(java.net.http.HttpClient admin, String body) throws Exception {
        var draft = new com.englishhero.reading.ReadingDtos.DraftInput("1", "Long translation " + System.nanoTime(),
                "长篇翻译测试", "A long translation test.", "B1", body, "Test Author", "ORIGINAL", null, "原创测试");
        var created = json.readTree(call(admin, "POST", "/admin/articles", draft).body());
        String id = created.path("id").asText();
        call(admin, "POST", "/admin/articles/" + id + "/publish",
                Map.of("expectedLockVersion", created.path("lockVersion").asInt()));
        return Map.of("articleId", id, "versionId", created.path("draftVersionId").asText());
    }

    private String path(Map<String, ?> article) {
        return "/reading/articles/" + article.get("articleId") + "/versions/" + article.get("versionId") + "/full-translation";
    }

    @AfterEach
    void releaseTestQuotaReservations() {
        if (!owners.isEmpty()) {
            String ids = owners.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            for (var row : jdbc.queryForList("""
                    SELECT a.quota_month,SUM(a.source_characters) AS characters
                    FROM reading_full_translation_attempt a
                    JOIN reading_full_translation_chunk c ON c.id=a.chunk_id
                    JOIN reading_article_version v ON v.id=c.version_id
                    JOIN reading_article article ON article.id=v.article_id
                    WHERE article.created_by IN (%s) AND a.status IN ('RESERVED','RETAINED')
                    GROUP BY a.quota_month
                    """.formatted(ids))) {
                jdbc.update("UPDATE reading_baidu_monthly_quota SET used_characters=GREATEST(0,used_characters-?) WHERE month_start=?",
                        row.get("characters"), row.get("quota_month"));
            }
        }
        baiduProperties.setMonthlyCharacterLimit(previousMonthlyLimit);
        fullProperties.setEnabled(previousEnabled);
    }
}
