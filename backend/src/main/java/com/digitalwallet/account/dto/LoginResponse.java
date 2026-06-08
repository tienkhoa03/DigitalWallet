package com.digitalwallet.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /auth/login} response body — the OAuth2-shaped bearer-token envelope.
 *
 * <p>{@code token_type} is always {@code "Bearer"}; {@code expires_in} is the TTL in
 * seconds (Open Q #2: 3600 s in MVP).
 */
public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {
}
