package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.englishhero.identity.IdentityException;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EmailServiceBoundaryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final PersistentRateLimiter limiter = mock(PersistentRateLimiter.class);
    private final EmailProperties properties = new EmailProperties();
    private final StaticListableBeanFactory beans = new StaticListableBeanFactory();
    private final EmailVerificationService service = new EmailVerificationService(jdbc, transactions, properties,
        beans.getBeanProvider(EmailDelivery.class), (TaskExecutor) Runnable::run, limiter, Clock.systemUTC());

    @AfterEach
    void clearTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void missingMailConfigurationReturnsGenericUnavailableWithoutPersistingOrThrottling() {
        assertThatThrownBy(() -> service.issue(EmailPurpose.REGISTER, "learner@example.com", null, null, true, "127.0.0.1"))
            .isInstanceOfSatisfying(IdentityException.class, error -> {
                assertThat(error.status).isEqualTo(503);
                assertThat(error.code).isEqualTo("EMAIL_UNAVAILABLE");
                assertThat(error.getMessage()).doesNotContain("learner@example.com");
            });
        verifyNoInteractions(jdbc, limiter);
    }

    @Test
    void verifiedAccountActionCannotBeNestedInsideAnExistingTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.executeVerified(EmailPurpose.REGISTER, "learner@example.com", null,
            null, "11111111-1111-1111-1111-111111111111", "123456", () -> "done"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("outside an account transaction");
        verifyNoInteractions(jdbc, transactions, limiter);
    }
}
