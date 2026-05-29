package com.digitalwallet.shared.exception;

/**
 * Base sealed type for every domain-level failure surfaced to clients.
 *
 * <p>Each subclass carries a stable {@code errorKey} (dot-separated identifier) that becomes the
 * {@code error_key} field of the canonical error envelope defined in
 * {@code docs/api/README.md §Error response shape}. The single
 * {@link DomainExceptionMapper} translates this hierarchy to HTTP responses; per-resource
 * try/catch blocks that hand-build JSON are a defect (see
 * {@code .claude/rules/backend_coding.md §8}).
 *
 * <p>The {@code permits} list is exhaustive: every error shape the platform surfaces must
 * map to one of these subclasses. New error shapes require a new permitted subclass and an
 * entry in {@link DomainExceptionMapper}.
 */
public abstract sealed class DomainException extends RuntimeException
        permits ValidationException,
                IdempotencyKeyRequiredException,
                AuthInvalidCredentialsException,
                AuthForbiddenException,
                ConflictException,
                BusinessRuleException,
                RateLimitException,
                CircuitOpenException {

    private final String errorKey;

    protected DomainException(String errorKey, String message) {
        super(message);
        this.errorKey = errorKey;
    }

    protected DomainException(String errorKey, String message, Throwable cause) {
        super(message, cause);
        this.errorKey = errorKey;
    }

    public String errorKey() {
        return errorKey;
    }
}
