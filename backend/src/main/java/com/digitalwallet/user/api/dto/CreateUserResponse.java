package com.digitalwallet.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /users} response body. Returns the newly-minted user id and creation
 * timestamp only — never email, role, or password hash
 * ({@code .claude/rules/security.md §7}).
 */
public record CreateUserResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("created_at") Instant createdAt) {
}
