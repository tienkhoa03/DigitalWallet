package com.digitalwallet.account.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /auth/login} request body.
 *
 * <p>{@code @Email} on the email; {@code @NotBlank} on the password — the length /
 * complexity rule lives on signup, login simply requires a non-empty string so that an
 * empty password cannot ever match.
 */
public record LoginRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String password) {
}
