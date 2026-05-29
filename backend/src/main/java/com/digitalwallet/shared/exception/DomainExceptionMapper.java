package com.digitalwallet.shared.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

/**
 * Global JAX-RS exception mapper for the {@link DomainException} hierarchy.
 *
 * <p>Status-code mapping per {@code .claude/rules/backend_coding.md §8}. Per-resource
 * try/catch blocks that hand-build JSON are a defect — every domain-level failure
 * surfaces through this single mapper.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    private static final Logger LOG = Logger.getLogger(DomainExceptionMapper.class);

    @Override
    public Response toResponse(DomainException ex) {
        int status = statusFor(ex);

        // Log at the level appropriate to the failure class. Never log payload contents.
        if (status >= 500) {
            LOG.errorf(ex, "Domain exception [%s] -> %d", ex.errorKey(), status);
        } else {
            LOG.debugf("Domain exception [%s] -> %d", ex.errorKey(), status);
        }

        Response.ResponseBuilder builder = Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ex.errorKey(), ex.getMessage()));

        if (ex instanceof RateLimitException rle) {
            builder.header("Retry-After", Long.toString(rle.retryAfterSeconds()));
        }

        return builder.build();
    }

    private static int statusFor(DomainException ex) {
        // Pattern-matching switch — enumerates every permitted subclass. We return the
        // integer code directly because 422 and 429 are not in the JDK Status enum.
        return switch (ex) {
            case ValidationException v -> 400;
            case IdempotencyKeyRequiredException i -> 400;
            case AuthInvalidCredentialsException a -> 401;
            case AuthForbiddenException a -> 403;
            case ConflictException c -> 409;
            case BusinessRuleException b -> 422;
            case RateLimitException r -> 429;
            case CircuitOpenException c -> 503;
        };
    }
}
