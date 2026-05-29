package com.digitalwallet.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Canonical error envelope from {@code docs/api/README.md §Error response shape}.
 *
 * <p>Clients branch on {@code error_key}; the {@code message} is informational only.
 * The optional {@code details} array carries field-level violations when Hibernate
 * Validator rejects a payload (see {@link ConstraintViolationExceptionMapper}).
 *
 * <p>The wire-shape field names use snake_case ({@code error_key}) to match the
 * project-wide JSON convention; the Java accessor remains camelCase to match
 * idiomatic Java.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("error_key") String errorKey,
        String message,
        List<FieldError> details) {

    public ErrorResponse(String errorKey, String message) {
        this(errorKey, message, null);
    }

    public record FieldError(String field, String message) {
    }
}
