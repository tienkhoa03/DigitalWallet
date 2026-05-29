package com.digitalwallet.shared.exception;

/**
 * 400 Bad Request — request payload failed structural or semantic validation.
 * Hibernate Validator failures are routed here via {@link ConstraintViolationExceptionMapper}.
 */
public final class ValidationException extends DomainException {

    public ValidationException(String errorKey, String message) {
        super(errorKey, message);
    }
}
