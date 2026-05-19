package com.digitalwallet.shared.exception;

/**
 * Sealed base for every recoverable domain failure.
 * Contract: backend_coding.md §8 — carries a stable {@code errorKey} consumed by clients.
 * Status mapping owned by {@link DomainExceptionMapper}.
 */
public sealed abstract class DomainException extends RuntimeException permits
        ValidationException,
        IdempotencyKeyRequiredException,
        AuthInvalidCredentialsException,
        AuthForbiddenException,
        AccountSuspendedException,
        ConflictException,
        BusinessRuleException,
        RateLimitException,
        CircuitOpenException,
        AuditFailureException {

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
