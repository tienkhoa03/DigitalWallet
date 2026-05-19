package com.digitalwallet.shared.exception;

/** HTTP 403; errorKey {@code auth.forbidden} — security.md §3, backend_coding.md §8. */
public final class AuthForbiddenException extends DomainException {
    public AuthForbiddenException() {
        super("auth.forbidden", "Caller is not authorised for this resource");
    }

    public AuthForbiddenException(String message) {
        super("auth.forbidden", message);
    }
}
