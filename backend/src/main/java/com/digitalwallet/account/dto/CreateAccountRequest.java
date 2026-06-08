package com.digitalwallet.account.dto;

import com.digitalwallet.shared.validation.CurrencyCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /accounts} request body.
 *
 * <p>Field constraints derive from {@code docs/business-rules/core-wallet-management-rules.md}
 * FR1.1, the Phase 1 plan Open Q #7 (Hibernate Validator default {@code @Email}), and
 * Open Q #8 (length-only password rule, 12..128).
 */
public record CreateAccountRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String password,

        @NotNull
        @CurrencyCode
        @JsonProperty("base_currency")
        String baseCurrency) {
}
