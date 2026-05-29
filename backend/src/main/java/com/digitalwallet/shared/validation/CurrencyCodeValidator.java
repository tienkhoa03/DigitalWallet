package com.digitalwallet.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a string against the JVM's ISO 4217 currency set
 * ({@link Currency#getAvailableCurrencies()}).
 *
 * <p>Rejects {@code null}, empty, length-other-than-3, and lowercase / mixed-case input —
 * ISO 4217 codes are uppercase by spec (see {@code docs/api/README.md §Conventions}).
 */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final Set<String> VALID_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() != 3) {
            return false;
        }
        // ISO 4217 codes are uppercase. We do NOT silently uppercase the input — that would
        // hide the contract from the client.
        if (!value.equals(value.toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        return VALID_CODES.contains(value);
    }
}
