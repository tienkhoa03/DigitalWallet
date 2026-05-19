package com.digitalwallet.shared.exception;

/**
 * HTTP 403; errorKey {@code account.suspended} — FR2.4 synchronous suspension block (NFR9).
 * See .claude/rules/backend_coding.md §3 (synchronous fraud pre-check) and §8.
 */
public final class AccountSuspendedException extends DomainException {
    public AccountSuspendedException() {
        super("account.suspended", "Account is suspended");
    }
}
