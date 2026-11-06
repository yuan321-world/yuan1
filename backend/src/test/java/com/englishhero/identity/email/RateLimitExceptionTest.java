package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitExceptionTest {
    @Test
    void exposesAPositiveRetryDelayAndStandardIdentityError() {
        var error = new RateLimitException(0);

        assertThat(error.status).isEqualTo(429);
        assertThat(error.code).isEqualTo("RATE_LIMITED");
        assertThat(error.retryAfter()).isEqualTo(1);
    }
}
