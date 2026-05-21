package com.digitalwallet.shared.exception;

/**
 * HTTP 409. errorKey family per backend_coding.md §8:
 * {@code wallet.duplicate_label}, {@code budget.duplicate_month},
 * {@code idempotency.replay_conflict}, {@code wallet.locked}.
 */
public final class ConflictException extends DomainException {
    public ConflictException(String errorKey, String message) {
        super(errorKey, message);
    }
}
