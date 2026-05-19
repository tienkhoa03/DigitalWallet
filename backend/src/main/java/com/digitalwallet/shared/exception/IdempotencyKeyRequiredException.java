package com.digitalwallet.shared.exception;

/** HTTP 400; errorKey {@code idempotency.key_required} — backend_coding.md §8. */
public final class IdempotencyKeyRequiredException extends DomainException {
    public IdempotencyKeyRequiredException() {
        super("idempotency.key_required", "Idempotency-Key header is required");
    }
}
