package com.digitalwallet.shared.exception;

/**
 * 403 Forbidden — the authenticated principal does not have access to the resource.
 * No caller until Phase 2 (first owner-scoped path parameter); shipped for the sealed
 * {@code permits} list.
 */
public final class AuthForbiddenException extends DomainException {

    public AuthForbiddenException(String message) {
        super("auth.forbidden", message);
    }

    public AuthForbiddenException(String errorKey, String message) {
        super(errorKey, message);
    }
}
