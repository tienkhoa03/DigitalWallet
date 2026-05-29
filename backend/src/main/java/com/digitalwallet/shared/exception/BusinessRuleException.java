package com.digitalwallet.shared.exception;

/**
 * 422 Unprocessable Entity — a domain invariant was violated despite the payload
 * being structurally well-formed (e.g. insufficient funds, FX rate missing,
 * synchronous fraud block). No caller until Phase 3; shipped for the sealed
 * {@code permits} list.
 */
public final class BusinessRuleException extends DomainException {

    public BusinessRuleException(String errorKey, String message) {
        super(errorKey, message);
    }
}
