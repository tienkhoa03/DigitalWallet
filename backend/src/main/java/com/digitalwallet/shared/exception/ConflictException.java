package com.digitalwallet.shared.exception;

/**
 * 409 Conflict — a uniqueness or idempotency-replay invariant was violated.
 * Phase 1 uses this for {@code account.email_taken}; later phases subclass for
 * {@code wallet.duplicate_label}, {@code budget.duplicate_month},
 * {@code idempotency.replay_conflict}, {@code wallet.locked}.
 */
public final class ConflictException extends DomainException {

    public ConflictException(String errorKey, String message) {
        super(errorKey, message);
    }
}
