package com.englishhero.identity.email;

import com.englishhero.identity.IdentityException;

public final class RateLimitException extends IdentityException {
    private final long retryAfter;

    public RateLimitException(long retryAfterSeconds) {
        super(429, "RATE_LIMITED", "请求过于频繁，请稍后重试");
        this.retryAfter = Math.max(1, retryAfterSeconds);
    }

    public long retryAfter() {
        return retryAfter;
    }
}
