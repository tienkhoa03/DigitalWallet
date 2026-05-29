package com.digitalwallet.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyCodeValidatorTest {

    private final CurrencyCodeValidator validator = new CurrencyCodeValidator();

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "JPY", "VND", "GBP"})
    void isValid_givenIso4217Code_returnsTrue(String code) {
        assertThat(validator.isValid(code, null)).isTrue();
    }

    @Test
    void isValid_givenNull_returnsFalse() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "US", "USDX", "USDD"})
    void isValid_givenWrongLength_returnsFalse(String code) {
        assertThat(validator.isValid(code, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "Usd", "uSd", "eur"})
    void isValid_givenLowerOrMixedCase_returnsFalse(String code) {
        // ISO 4217 codes are uppercase by definition; we do not silently uppercase the input.
        assertThat(validator.isValid(code, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"XYZ", "ABC", "ZZZ"})
    void isValid_givenUnknownCode_returnsFalse(String code) {
        assertThat(validator.isValid(code, null)).isFalse();
    }
}
