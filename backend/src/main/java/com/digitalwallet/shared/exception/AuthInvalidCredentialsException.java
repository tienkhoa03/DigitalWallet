package com.digitalwallet.shared.exception;

/** HTTP 401; errorKey {@code auth.invalid_credentials} — security.md §2 (enumeration prevention). */
public final class AuthInvalidCredentialsException extends DomainException {
    public AuthInvalidCredentialsException() {
        super("auth.invalid_credentials", "Invalid credentials");
    }
}
