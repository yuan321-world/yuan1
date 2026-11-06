package com.englishhero.identity.email;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PersistentRateLimiter {
    private static final String CREATE_BUCKET = "INSERT INTO identity_auth_rate "
        + "(scope,subject_hash,window_seconds,event_times,expires_at,updated_at) "
        + "VALUES(?,?,?,'',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) "
        + "ON DUPLICATE KEY UPDATE scope=identity_auth_rate.scope";
    private static final String READ_BUCKET = "SELECT event_times FROM identity_auth_rate "
        + "WHERE scope=? AND subject_hash=? AND window_seconds=? FOR UPDATE";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate independentTransaction;

    public PersistentRateLimiter(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(manager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Counts every attempt, including successful authentication, by username+IP and by IP. */
    public void loginAttempt(String username, String remoteIp) {
        String normalizedUsername = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        String ip = remoteIp == null || remoteIp.isBlank() ? "unknown" : remoteIp.strip();
        independentTransaction.executeWithoutResult(status -> {
            check("LOGIN_USER_IP", normalizedUsername + "\u0000" + ip, 900, 25);
            check("LOGIN_IP", ip, 900, 100);
        });
    }

    /** Shared email cooldown applies across purposes; email and source-IP windows are rolling. */
    public void emailIssue(String email, String remoteIp) {
        String ip = remoteIp == null || remoteIp.isBlank() ? "unknown" : remoteIp.strip();
        independentTransaction.executeWithoutResult(status -> {
            check("EMAIL_SEND", email, 60, 1);
            check("EMAIL_HOUR", email, 3_600, 5);
            check("EMAIL_DAY", email, 86_400, 10);
            check("EMAIL_IP_HOUR", ip, 3_600, 20);
        });
    }

    private void check(String scope, String subject, int windowSeconds, int maximum) {
        byte[] subjectHash = EmailDigests.sha256(scope + "\u0000" + subject);
        jdbc.update(CREATE_BUCKET, scope, subjectHash, windowSeconds);
        String encoded = jdbc.queryForObject(READ_BUCKET, String.class, scope, subjectHash, windowSeconds);
        long now = clock.millis();
        long cutoff = now - windowSeconds * 1_000L;
        List<Long> recent = parseEvents(encoded).stream().filter(at -> at > cutoff && at <= now).sorted().toList();
        if (recent.size() >= maximum) {
            long retryAfter = Math.max(1, (recent.get(0) + windowSeconds * 1_000L - now + 999) / 1_000);
            throw new RateLimitException(retryAfter);
        }
        List<Long> updated = new ArrayList<>(recent);
        updated.add(now);
        StringBuilder eventTimes = new StringBuilder();
        for (long at : updated) {
            if (!eventTimes.isEmpty()) eventTimes.append(',');
            eventTimes.append(at);
        }
        Instant expires = Instant.ofEpochMilli(now).plusSeconds(windowSeconds);
        jdbc.update("UPDATE identity_auth_rate SET event_times=?,expires_at=?,updated_at=UTC_TIMESTAMP(6) "
                + "WHERE scope=? AND subject_hash=? AND window_seconds=?",
            eventTimes.toString(), Timestamp.from(expires), scope, subjectHash, windowSeconds);
    }

    private List<Long> parseEvents(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        List<Long> result = new ArrayList<>();
        for (String item : encoded.split(",", -1)) {
            try {
                result.add(Long.parseLong(item));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid persisted rate-limit state");
            }
        }
        return result;
    }
}
