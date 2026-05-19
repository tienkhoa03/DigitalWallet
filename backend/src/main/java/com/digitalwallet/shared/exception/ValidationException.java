package com.digitalwallet.shared.exception;

/** HTTP 400. errorKey family: {@code validation.*} — backend_coding.md §8. */
public final class ValidationException extends DomainException {

    public ValidationException(String errorKey, String message) {
        super(errorKey, message);
    }

    public static ValidationException invalidPayload(String message) {
        return new ValidationException("validation.invalid_payload", message);
    }
}
