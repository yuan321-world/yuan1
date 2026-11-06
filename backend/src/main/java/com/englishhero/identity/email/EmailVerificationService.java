package com.englishhero.identity.email;

import com.englishhero.identity.IdentityException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailVerificationService {
    private static final int CHALLENGE_LIFETIME_SECONDS = 300;
    private static final int RESEND_DELAY_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final String SELECT_CHALLENGE = "SELECT purpose,email_hash,user_id,security_version,code_hmac,"
        + "expires_at,attempts,consumed_at,disabled_at FROM identity_email_challenge WHERE challenge_id=? FOR UPDATE";

    private final JdbcTemplate jdbc;
    private final EmailProperties properties;
    private final EmailDelivery delivery;
    private final TaskExecutor deliveryExecutor;
    private final PersistentRateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final TransactionTemplate independentTransaction;
    private final TransactionTemplate accountTransaction;

    public EmailVerificationService(JdbcTemplate jdbc, PlatformTransactionManager manager,
            EmailProperties properties, ObjectProvider<EmailDelivery> deliveryProvider,
            @Qualifier("emailDeliveryExecutor") TaskExecutor deliveryExecutor,
            PersistentRateLimiter rateLimiter, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.delivery = deliveryProvider.getIfAvailable();
        this.deliveryExecutor = deliveryExecutor;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(manager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.accountTransaction = new TransactionTemplate(manager);
    }

    public ChallengeDto issue(EmailPurpose purpose, String email, Long userId, Integer securityVersion,
            boolean deliver, String remoteIp) {
        if (purpose == null) throw IdentityException.invalid("purpose", "验证码用途不正确");
        String mailbox = EmailRules.email(email);
        if ((userId == null) != (securityVersion == null))
            throw IdentityException.invalid("email", "验证码请求无效");
        requireSendReady();
        rateLimiter.emailIssue(mailbox, remoteIp);

        String challengeId = UUID.randomUUID().toString();
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        byte[] emailHash = EmailDigests.sha256(mailbox);
        byte[] codeHmac = codeHmac(challengeId, purpose, mailbox, userId, securityVersion, code);
        Timestamp expiresAt = Timestamp.from(clock.instant().plusSeconds(CHALLENGE_LIFETIME_SECONDS));
        Timestamp resendAt = Timestamp.from(clock.instant().plusSeconds(RESEND_DELAY_SECONDS));
        independentTransaction.executeWithoutResult(status -> {
            // Ineligible public requests retain the same response/quota but must not revoke real proofs.
            jdbc.update("UPDATE identity_email_challenge SET disabled_at=UTC_TIMESTAMP(6) "
                    + "WHERE purpose=? AND email_hash=? AND consumed_at IS NULL AND disabled_at IS NULL AND ?=1",
                purpose.name(), emailHash, deliver ? 1 : 0);
            jdbc.update("INSERT INTO identity_email_challenge "
                    + "(challenge_id,purpose,email_hash,user_id,security_version,code_hmac,expires_at,resend_after,attempts,delivery_state) "
                    + "VALUES(?,?,?,?,?,?,?,?,0,?)", challengeId, purpose.name(), emailHash, userId,
                securityVersion, codeHmac, expiresAt, resendAt, deliver ? "QUEUED" : "NOT_SENT");
        });

        try {
            deliveryExecutor.execute(() -> {
                if (!deliver) return;
                try {
                    delivery.deliver(mailbox, code, purpose);
                    jdbc.update("UPDATE identity_email_challenge SET delivery_state='SMTP_ACCEPTED' WHERE challenge_id=? AND delivery_state='QUEUED'", challengeId);
                } catch (RuntimeException ignored) {
                    disable(challengeId);
                }
            });
        } catch (RuntimeException rejected) {
            disable(challengeId);
            throw unavailable();
        }
        return new ChallengeDto(challengeId, CHALLENGE_LIFETIME_SECONDS, RESEND_DELAY_SECONDS);
    }

    /** No public status endpoint: only an authenticated BIND proof owner may inspect transport state. */
    public DeliveryStatus bindingStatus(String challengeId, long userId, int securityVersion) {
        if (!isUuid(challengeId)) throw statusNotFound();
        var rows = jdbc.query("SELECT delivery_state,expires_at,disabled_at FROM identity_email_challenge "
                + "WHERE challenge_id=? AND purpose='BIND' AND user_id=? AND security_version=?",
            (rs, row) -> new DeliveryStatus(rs.getTimestamp("disabled_at") != null ? "FAILED"
                : !clock.instant().isBefore(rs.getTimestamp("expires_at").toInstant()) ? "EXPIRED"
                : rs.getString("delivery_state")), challengeId, userId, securityVersion);
        if (rows.isEmpty()) throw statusNotFound();
        return rows.getFirst();
    }

    private IdentityException statusNotFound() {
        return new IdentityException(404, "EMAIL_REQUEST_NOT_FOUND", "验证码请求不存在或已失效");
    }

    /**
     * Runs the successful account action in the same transaction that consumes the challenge.
     * The invalid-attempt transaction completes first, before a connection for account reads can
     * be held, avoiding nested-transaction pool starvation on parallel requests.
     */
    public <T> T executeVerified(EmailPurpose purpose, String email, Long userId, Integer securityVersion,
            String challengeId, String code, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Verified email actions must start outside an account transaction");
        if (action == null) throw new IllegalArgumentException("Verified email action is required");
        String mailbox = EmailRules.email(email);
        if (purpose == null || !isUuid(challengeId) || code == null || !code.matches("[0-9]{6}"))
            throw invalidProof();
        requireHmacReady();
        byte[] emailHash = EmailDigests.sha256(mailbox);
        byte[] expectedCodeHmac = codeHmac(challengeId, purpose, mailbox, userId, securityVersion, code);

        Boolean validAttempt = independentTransaction.execute(status -> {
            Challenge challenge = findChallenge(challengeId);
            if (challenge == null || !matches(challenge, purpose, emailHash, userId, securityVersion)
                    || !challenge.isActive(clock.instant())) return false;
            if (MessageDigest.isEqual(challenge.codeHmac(), expectedCodeHmac)) return true;
            jdbc.update("UPDATE identity_email_challenge SET "
                    + "disabled_at=CASE WHEN attempts+1>=? THEN UTC_TIMESTAMP(6) ELSE disabled_at END, "
                    + "attempts=attempts+1 "
                    + "WHERE challenge_id=? AND attempts=? AND consumed_at IS NULL AND disabled_at IS NULL",
                MAX_ATTEMPTS, challengeId, challenge.attempts());
            return false;
        });
        if (!Boolean.TRUE.equals(validAttempt)) throw invalidProof();

        return accountTransaction.execute(status -> {
            Challenge challenge = findChallenge(challengeId);
            if (challenge == null || !matches(challenge, purpose, emailHash, userId, securityVersion)
                    || !challenge.isActive(clock.instant())
                    || !MessageDigest.isEqual(challenge.codeHmac(), expectedCodeHmac)) {
                throw invalidProof();
            }
            int consumed = jdbc.update("UPDATE identity_email_challenge SET consumed_at=UTC_TIMESTAMP(6) "
                    + "WHERE challenge_id=? AND consumed_at IS NULL AND disabled_at IS NULL AND attempts<? AND expires_at>?",
                challengeId, MAX_ATTEMPTS, Timestamp.from(clock.instant()));
            if (consumed != 1) throw invalidProof();
            return action.get();
        });
    }

    private Challenge findChallenge(String challengeId) {
        try {
            return jdbc.queryForObject(SELECT_CHALLENGE, CHALLENGE_MAPPER, challengeId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private byte[] codeHmac(String challengeId, EmailPurpose purpose, String email, Long userId,
            Integer securityVersion, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = purpose.name() + "\u0000" + email + "\u0000" + Objects.toString(userId, "")
                + "\u0000" + Objects.toString(securityVersion, "") + "\u0000" + challengeId + "\u0000" + code;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC is unavailable", e);
        }
    }

    private void requireSendReady() {
        if (!properties.isEnabled() || !hasValidSecret() || delivery == null || !delivery.available())
            throw unavailable();
        try {
            EmailRules.email(properties.getFrom());
        } catch (IdentityException e) {
            throw unavailable();
        }
    }

    private void requireHmacReady() {
        if (!properties.isEnabled() || !hasValidSecret()) throw unavailable();
    }

    private boolean hasValidSecret() {
        return properties.getHmacSecret().getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    private void disable(String challengeId) {
        try {
            jdbc.update("UPDATE identity_email_challenge SET disabled_at=UTC_TIMESTAMP(6),delivery_state='FAILED' "
                + "WHERE challenge_id=? AND consumed_at IS NULL", challengeId);
        } catch (RuntimeException ignored) {
            // Delivery failures must not surface recipient, transport, or SMTP details.
        }
    }

    private boolean matches(Challenge challenge, EmailPurpose purpose, byte[] emailHash, Long userId,
            Integer securityVersion) {
        return challenge.purpose().equals(purpose.name())
            && MessageDigest.isEqual(challenge.emailHash(), emailHash)
            && Objects.equals(challenge.userId(), userId)
            && Objects.equals(challenge.securityVersion(), securityVersion);
    }

    private boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private IdentityException invalidProof() {
        return IdentityException.invalid("code", "验证码无效或已过期，请重新获取");
    }

    private IdentityException unavailable() {
        return new IdentityException(503, "EMAIL_UNAVAILABLE", "邮件服务暂不可用，请稍后重试");
    }

    private static final RowMapper<Challenge> CHALLENGE_MAPPER = (ResultSet rs, int rowNum) -> {
        Long userId = nullableLong(rs, "user_id");
        Integer securityVersion = nullableInt(rs, "security_version");
        return new Challenge(rs.getString("purpose"), rs.getBytes("email_hash"), userId, securityVersion,
            rs.getBytes("code_hmac"), rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts"),
            instant(rs.getTimestamp("consumed_at")), instant(rs.getTimestamp("disabled_at")));
    };

    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record Challenge(String purpose, byte[] emailHash, Long userId, Integer securityVersion,
            byte[] codeHmac, Instant expiresAt, int attempts, Instant consumedAt, Instant disabledAt) {
        boolean isActive(Instant now) {
            return attempts < MAX_ATTEMPTS && consumedAt == null && disabledAt == null && now.isBefore(expiresAt);
        }
    }

    public record DeliveryStatus(String state) {}
    public record ChallengeDto(String challengeId, int expiresIn, int resendAfter) {
        public String getRequestState() { return "ACCEPTED"; }
    }
}
