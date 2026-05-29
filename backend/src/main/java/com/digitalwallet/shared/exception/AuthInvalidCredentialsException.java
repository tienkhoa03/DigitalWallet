package com.digitalwallet.shared.exception;

/**
 * 401 Unauthorized — login failed. The message is identical on every failure branch
 * (unknown email, wrong password) per the enumeration-resistance rule in
 * {@code .claude/rules/security.md §2}.
 */
public final class AuthInvalidCredentialsException extends DomainException {

    public AuthInvalidCredentialsException(String message) {
        super("auth.invalid_credentials", message);
    }
}
