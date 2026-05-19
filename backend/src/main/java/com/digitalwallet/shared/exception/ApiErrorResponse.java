package com.digitalwallet.shared.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Canonical error envelope; shape defined in docs/api/README.md "Error response shape".
 */
public record ApiErrorResponse(
        @JsonProperty("error_key") String errorKey,
        @JsonProperty("message") String message
) {
}
