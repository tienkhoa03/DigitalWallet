package com.digitalwallet.shared.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Maps Hibernate Validator's {@link ConstraintViolationException} onto the canonical error
 * envelope with {@code error_key = "validation.invalid_payload"} and a {@code details}
 * array of field-level violations, per {@code .claude/rules/backend_coding.md §8}.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        List<ErrorResponse.FieldError> details = ex.getConstraintViolations().stream()
                .map(ConstraintViolationExceptionMapper::toFieldError)
                .toList();

        ErrorResponse body = new ErrorResponse(
                "validation.invalid_payload",
                "Request payload failed validation",
                details);

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private static ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        // Path looks like "signup.arg0.email" — strip everything before the leaf property
        // so clients see "email" not the resource method name.
        String field = lastNode(violation.getPropertyPath());
        return new ErrorResponse.FieldError(field, violation.getMessage());
    }

    private static String lastNode(Path path) {
        String last = "";
        for (Path.Node node : path) {
            String name = node.getName();
            if (name != null && !name.isBlank()) {
                last = name;
            }
        }
        return last;
    }
}
