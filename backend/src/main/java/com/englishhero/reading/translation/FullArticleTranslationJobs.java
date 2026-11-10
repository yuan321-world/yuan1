package com.englishhero.reading.translation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.englishhero.reading.ArticleContent;
import com.englishhero.reading.ReadingDtos.ArticleVersionDto;
import static com.englishhero.reading.translation.FullArticleTranslationDtos.*;

@Service
public class FullArticleTranslationJobs {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final BaiduTranslationProperties baidu;

    public FullArticleTranslationJobs(JdbcTemplate jdbc, Clock clock, BaiduTranslationProperties baidu) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.baidu = baidu;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int enqueue(ArticleVersionDto version, String contentHash) {
        Instant now = clock.instant();
        int inserted = 0;
        for (var paragraph : version.content().paragraphs()) {
            String source = paragraph.sentences().stream().map(ArticleContent.Sentence::text)
                    .collect(java.util.stream.Collectors.joining(" "));
            var chunks = FullArticleTranslationChunks.split(source);
            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);
                inserted += jdbc.update("""
                        INSERT IGNORE INTO reading_full_translation_chunk
                        (version_id,content_hash,paragraph_key,chunk_index,source_text,separator_before,status,available_at,created_at)
                        VALUES(?,?,?,?,?,?,'QUEUED',?,?)
                        """, Long.parseLong(version.id()), contentHash, paragraph.key(), i, chunk.text(),
                        chunk.separatorBefore(), Timestamp.from(now), Timestamp.from(now));
            }
        }
        jdbc.update("""
                UPDATE reading_full_translation_chunk
                SET status='QUEUED',error_code=NULL,available_at=?,retry_after=NULL,
                    lease_token=NULL,lease_until=NULL,completed_at=NULL,rate_limit_attempts=0
                WHERE version_id=? AND content_hash=? AND status='FAILED' AND retry_after IS NOT NULL AND retry_after<=?
                """, Timestamp.from(now), Long.parseLong(version.id()), contentHash, Timestamp.from(now));
        return inserted;
    }

    @Transactional(readOnly = true)
    public List<ChunkRow> rows(long versionId, String contentHash) {
        return jdbc.query("""
                SELECT paragraph_key,chunk_index,separator_before,status,translated_text,error_code,retry_after
                FROM reading_full_translation_chunk WHERE version_id=? AND content_hash=?
                ORDER BY paragraph_key,chunk_index
                """, (r, n) -> new ChunkRow(r.getString("paragraph_key"), r.getInt("chunk_index"),
                r.getString("separator_before"), r.getString("status"), r.getString("translated_text"),
                r.getString("error_code"), instant(r, "retry_after")), versionId, contentHash);
    }

    @Transactional(readOnly = true)
    public List<PublishedVersion> publishedVersions() {
        return jdbc.query("""
                SELECT id,published_version_id FROM reading_article
                WHERE content_usage='READING' AND status='PUBLISHED' AND published_version_id IS NOT NULL ORDER BY id
                """, (r, n) -> new PublishedVersion(r.getLong("id"), r.getLong("published_version_id")));
    }

    @Transactional
    public boolean beginSeed() {
        return jdbc.update("INSERT IGNORE INTO reading_full_translation_seed(id,seeded_at) VALUES(1,?)",
                Timestamp.from(clock.instant())) == 1;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recoverExpired() {
        Instant now = clock.instant();
        var ids = jdbc.query("""
                SELECT id,attempt_count FROM reading_full_translation_chunk
                WHERE status='RUNNING' AND lease_until<? FOR UPDATE SKIP LOCKED
                """, (r, n) -> new long[]{r.getLong("id"), r.getInt("attempt_count")}, Timestamp.from(now));
        for (long[] row : ids) {
            retainAttempt(row[0], row[1], now);
            jdbc.update("""
                    UPDATE reading_full_translation_chunk
                    SET status='FAILED',error_code='OUTCOME_UNKNOWN',retry_after=?,completed_at=?,
                        lease_token=NULL,lease_until=NULL
                    WHERE id=? AND status='RUNNING'
                    """, Timestamp.from(now.plusSeconds(1800)), Timestamp.from(now), row[0]);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ChunkJob> claim() {
        Instant now = clock.instant();
        var rows = jdbc.query("""
                SELECT id,version_id,content_hash,paragraph_key,chunk_index,source_text,
                       attempt_count,rate_limit_attempts
                FROM reading_full_translation_chunk
                WHERE status='QUEUED' AND available_at<=?
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (r, n) -> new PendingChunk(r.getLong("id"), r.getLong("version_id"),
                r.getString("content_hash"), r.getString("paragraph_key"), r.getInt("chunk_index"),
                r.getString("source_text"), r.getInt("attempt_count"), r.getInt("rate_limit_attempts")),
                Timestamp.from(now));
        if (rows.isEmpty()) return Optional.empty();
        var pending = rows.getFirst();

        LocalDate month = now.atZone(BEIJING).toLocalDate().withDayOfMonth(1);
        int characters = baidu.characters(pending.text());
        jdbc.update("INSERT INTO reading_baidu_monthly_quota(month_start) VALUES(?) ON DUPLICATE KEY UPDATE month_start=VALUES(month_start)", month);
        long used = jdbc.queryForObject("SELECT used_characters FROM reading_baidu_monthly_quota WHERE month_start=? FOR UPDATE", Long.class, month);
        if (characters > Math.max(0, baidu.getMonthlyCharacterLimit()) - used) {
            Instant nextMonth = month.plusMonths(1).atStartOfDay(BEIJING).toInstant();
            jdbc.update("""
                    UPDATE reading_full_translation_chunk
                    SET status='FAILED',error_code='BAIDU_CHARACTER_QUOTA',retry_after=?,completed_at=?
                    WHERE id=? AND status='QUEUED'
                    """, Timestamp.from(nextMonth), Timestamp.from(now), pending.id());
            return Optional.empty();
        }

        jdbc.update("UPDATE reading_baidu_monthly_quota SET used_characters=used_characters+? WHERE month_start=?", characters, month);
        int attemptNo = pending.attemptCount() + 1;
        int rateAttempts = pending.rateLimitAttempts() + 1;
        jdbc.update("""
                INSERT INTO reading_full_translation_attempt
                (chunk_id,attempt_no,quota_month,source_characters,status,started_at)
                VALUES(?,?,?,?,'RESERVED',?)
                """, pending.id(), attemptNo, month, characters, Timestamp.from(now));
        String lease = UUID.randomUUID().toString();
        Instant leaseUntil = now.plusSeconds(90);
        jdbc.update("""
                UPDATE reading_full_translation_chunk
                SET status='RUNNING',attempt_count=?,rate_limit_attempts=?,lease_token=?,lease_until=?
                WHERE id=?
                """, attemptNo, rateAttempts, lease, Timestamp.from(leaseUntil), pending.id());
        return Optional.of(new ChunkJob(pending.id(), pending.versionId(), pending.contentHash(), pending.paragraphKey(),
                pending.chunkIndex(), pending.text(), attemptNo, rateAttempts, month, lease, leaseUntil));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void succeed(ChunkJob job, BaiduTranslationClient.Result result) {
        if (!current(job)) return;
        jdbc.update("""
                UPDATE reading_full_translation_attempt SET status='RETAINED',settled_at=?
                WHERE chunk_id=? AND attempt_no=? AND status='RESERVED'
                """, Timestamp.from(clock.instant()), job.id(), job.attemptNo());
        jdbc.update("""
                UPDATE reading_full_translation_chunk
                SET status='SUCCEEDED',translated_text=?,error_code=NULL,retry_after=NULL,completed_at=?,
                    lease_token=NULL,lease_until=NULL
                WHERE id=?
                """, result.translation(), Timestamp.from(clock.instant()), job.id());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fail(ChunkJob job, String rawCode, boolean definitelyUnsent) {
        if (!current(job)) return;
        Instant now = clock.instant();
        String code = rawCode != null && rawCode.matches("[A-Z0-9_]{1,64}") ? rawCode : "OUTCOME_UNKNOWN";
        boolean rateLimited = code.equals("BAIDU_54003") || code.equals("BAIDU_54005");
        if (definitelyUnsent) releaseAttempt(job, now);
        else retainAttempt(job.id(), job.attemptNo(), now);

        if (rateLimited && job.rateLimitAttempts() < 3) {
            long delaySeconds = 20L << (job.rateLimitAttempts() - 1);
            jdbc.update("""
                    UPDATE reading_full_translation_chunk
                    SET status='QUEUED',error_code=NULL,retry_after=NULL,available_at=?,
                        completed_at=NULL,lease_token=NULL,lease_until=NULL
                    WHERE id=?
                    """, Timestamp.from(now.plusSeconds(delaySeconds)), job.id());
            return;
        }

        long cooldown = rateLimited || definitelyUnsent ? 300 : 1800;
        jdbc.update("""
                UPDATE reading_full_translation_chunk
                SET status='FAILED',error_code=?,retry_after=?,completed_at=?,lease_token=NULL,lease_until=NULL
                WHERE id=?
                """, code, Timestamp.from(now.plusSeconds(cooldown)), Timestamp.from(now), job.id());
    }

    private boolean current(ChunkJob job) {
        return !jdbc.query("""
                SELECT id FROM reading_full_translation_chunk
                WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>?
                FOR UPDATE
                """, (r, n) -> r.getLong(1), job.id(), job.leaseToken(), Timestamp.from(clock.instant())).isEmpty();
    }

    private void releaseAttempt(ChunkJob job, Instant now) {
        var attempts = jdbc.query("""
                SELECT quota_month,source_characters FROM reading_full_translation_attempt
                WHERE chunk_id=? AND attempt_no=? AND status='RESERVED' FOR UPDATE
                """, (r, n) -> new QuotaAttempt(r.getDate("quota_month").toLocalDate(), r.getInt("source_characters")),
                job.id(), job.attemptNo());
        if (attempts.isEmpty()) return;
        var attempt = attempts.getFirst();
        jdbc.update("""
                UPDATE reading_full_translation_attempt SET status='RELEASED',settled_at=?
                WHERE chunk_id=? AND attempt_no=? AND status='RESERVED'
                """, Timestamp.from(now), job.id(), job.attemptNo());
        jdbc.update("""
                UPDATE reading_baidu_monthly_quota SET used_characters=GREATEST(0,used_characters-?)
                WHERE month_start=?
                """, attempt.characters(), attempt.month());
    }

    private void retainAttempt(long chunkId, long attemptNo, Instant now) {
        jdbc.update("""
                UPDATE reading_full_translation_attempt SET status='RETAINED',settled_at=?
                WHERE chunk_id=? AND attempt_no=? AND status='RESERVED'
                """, Timestamp.from(now), chunkId, attemptNo);
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record PendingChunk(long id, long versionId, String contentHash, String paragraphKey,
                                int chunkIndex, String text, int attemptCount, int rateLimitAttempts) {}
    private record QuotaAttempt(LocalDate month, int characters) {}
}
