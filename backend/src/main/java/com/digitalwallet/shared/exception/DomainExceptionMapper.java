package com.digitalwallet.shared.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Single owner of the HTTP error envelope per backend_coding.md §8.
 * Per-resource try/catch is forbidden — surface a typed {@link DomainException} instead.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException ex) {
        Response.ResponseBuilder builder = Response
                .status(statusFor(ex))
                .entity(new ApiErrorResponse(ex.errorKey(), ex.getMessage()));

        if (ex instanceof RateLimitException rl) {
            builder.header("Retry-After", String.valueOf(rl.retryAfterSeconds()));
        }
        return builder.build();
    }

    private static int statusFor(DomainException ex) {
        return switch (ex) {
            case ValidationException ignored -> 400;
            case IdempotencyKeyRequiredException ignored -> 400;
            case AuthInvalidCredentialsException ignored -> 401;
            case AuthForbiddenException ignored -> 403;
            case AccountSuspendedException ignored -> 403;
            case ConflictException ignored -> 409;
            case BusinessRuleException ignored -> 422;
            case RateLimitException ignored -> 429;
            case CircuitOpenException ignored -> 503;
            case AuditFailureException ignored -> 500;
        };
    }
}
