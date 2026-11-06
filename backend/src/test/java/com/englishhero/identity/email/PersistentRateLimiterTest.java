package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.doNothing;

class PersistentRateLimiterTest {
    @Test
    void emailLimitUsesRollingWindowAndReturnsTimeUntilOldestAttemptExpires() {
        long now = 2_000_000_000_000L;
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(ignored -> new SimpleTransactionStatus());
        doNothing().when(manager).commit(any(TransactionStatus.class));
        doNothing().when(manager).rollback(any(TransactionStatus.class));
        String hourEvents = String.join(",", Long.toString(now - 300_000), Long.toString(now - 240_000),
            Long.toString(now - 180_000), Long.toString(now - 120_000), Long.toString(now - 60_000));
        Map<String, String> events = Map.of("EMAIL_SEND", Long.toString(now - 60_001), "EMAIL_HOUR", hourEvents);
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenAnswer(invocation -> {
            return events.getOrDefault(invocation.getArgument(2), "");
        });

        var limiter = new PersistentRateLimiter(jdbc, manager,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));

        assertThatThrownBy(() -> limiter.emailIssue("learner@example.com", "127.0.0.1"))
            .isInstanceOfSatisfying(RateLimitException.class, error -> {
                assertThat(error.retryAfter()).isEqualTo(3_300);
                assertThat(error.status).isEqualTo(429);
            });
    }
}
