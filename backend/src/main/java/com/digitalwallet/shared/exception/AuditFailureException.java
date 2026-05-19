package com.digitalwallet.shared.exception;

/** HTTP 500; errorKey {@code audit.write_failed} — backend_coding.md §8. */
public final class AuditFailureException extends DomainException {
    public AuditFailureException(String message, Throwable cause) {
        super("audit.write_failed", message, cause);
    }
}
