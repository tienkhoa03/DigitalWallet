package com.digitalwallet.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /accounts} response body. Returns the newly-minted account id and creation
 * timestamp only — never email, role, or password hash
 * ({@code .claude/rules/security.md §7}).
 */
public record CreateAccountResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("created_at") Instant createdAt) {
}
