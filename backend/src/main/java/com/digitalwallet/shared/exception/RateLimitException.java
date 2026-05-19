package com.digitalwallet.shared.exception;

/**
 * HTTP 429; errorKey {@code ratelimit.exceeded}. Mapper attaches the {@code Retry-After}
 * header from {@link #retryAfterSeconds()} — security.md §8, backend_coding.md §8.
 */
public final class RateLimitException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("ratelimit.exceeded", "Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
