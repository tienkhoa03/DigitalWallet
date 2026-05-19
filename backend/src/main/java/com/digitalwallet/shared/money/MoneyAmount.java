package com.digitalwallet.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Shared money value object.
 * Backed by {@link BigDecimal} (numeric(19,4) on disk) and ISO 4217 currency code —
 * .claude/rules/backend_coding.md §4 ("BigDecimal over double/float for money").
 */
public record MoneyAmount(BigDecimal value, String currencyCode) {

    public MoneyAmount {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (currencyCode.length() != 3) {
            throw new IllegalArgumentException("currencyCode must be ISO 4217 (3 letters)");
        }
    }
}
