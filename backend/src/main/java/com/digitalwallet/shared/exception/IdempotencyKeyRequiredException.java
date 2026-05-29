package com.digitalwallet.shared.exception;

/**
 * 400 Bad Request — a mutating money endpoint was called without the
 * {@code Idempotency-Key} header (NFR3). No caller until Phase 3; shipped for the
 * sealed {@code permits} list (see {@code .claude/rules/backend_coding.md §8}).
 */
public final class IdempotencyKeyRequiredException extends DomainException {

    public IdempotencyKeyRequiredException(String message) {
        super("idempotency.key_required", message);
    }
}
