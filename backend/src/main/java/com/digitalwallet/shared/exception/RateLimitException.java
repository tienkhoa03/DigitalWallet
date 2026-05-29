package com.digitalwallet.shared.exception;

/**
 * 429 Too Many Requests — the caller exceeded the Redis token-bucket budget
 * ({@code POST /transfers} 10/min, {@code POST /advisor/*} 5/hour). The mapper attaches
 * a {@code Retry-After} header. No caller until Phase 6; shipped for the sealed
 * {@code permits} list.
 */
public final class RateLimitException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super("ratelimit.exceeded", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
