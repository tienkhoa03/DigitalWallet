package com.digitalwallet.shared.exception;

/**
 * HTTP 422. errorKey family per backend_coding.md §8:
 * {@code wallet.insufficient_funds}, {@code transfer.fx_rate_missing},
 * {@code transfer.recipient_not_found}, {@code transfer.same_wallet},
 * {@code wallet.currency_mismatch}, {@code advisor.month_not_ready},
 * {@code validation.invalid_amount},
 * {@code fraud.velocity_exceeded}, {@code fraud.volume_exceeded} (FR2.1 / FR2.2).
 */
public final class BusinessRuleException extends DomainException {
    public BusinessRuleException(String errorKey, String message) {
        super(errorKey, message);
    }
}
