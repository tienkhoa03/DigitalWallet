package com.digitalwallet.shared.lock;

import com.digitalwallet.shared.exception.ConflictException;

/**
 * HTTP 409 {@code wallet.locked} per backend_coding.md §8 status table.
 * Surfaced when the Redis outer lock cannot be acquired before the wait budget elapses.
 */
public final class LockUnavailableException extends RuntimeException {

    public LockUnavailableException(String key) {
        super("Lock unavailable for key " + key);
    }

    public ConflictException asConflict() {
        return new ConflictException("wallet.locked", getMessage());
    }
}
