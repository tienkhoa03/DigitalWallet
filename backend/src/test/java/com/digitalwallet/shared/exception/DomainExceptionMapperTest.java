package com.digitalwallet.shared.exception;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionMapperTest {

    private final DomainExceptionMapper mapper = new DomainExceptionMapper();

    @Test
    void toResponse_validationException_returns400WithErrorKey() {
        Response response = mapper.toResponse(
                new ValidationException("validation.invalid_payload", "bad payload"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body -> {
            assertThat(body.errorKey()).isEqualTo("validation.invalid_payload");
            assertThat(body.message()).isEqualTo("bad payload");
        });
    }

    @Test
    void toResponse_idempotencyKeyRequired_returns400() {
        Response response = mapper.toResponse(
                new IdempotencyKeyRequiredException("missing Idempotency-Key"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("idempotency.key_required"));
    }

    @Test
    void toResponse_authInvalidCredentials_returns401() {
        Response response = mapper.toResponse(
                new AuthInvalidCredentialsException("Invalid credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("auth.invalid_credentials"));
    }

    @Test
    void toResponse_authForbidden_returns403() {
        Response response = mapper.toResponse(new AuthForbiddenException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("auth.forbidden"));
    }

    @Test
    void toResponse_conflictException_returns409() {
        Response response = mapper.toResponse(
                new ConflictException("user.email_taken", "Email is already registered"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("user.email_taken"));
    }

    @Test
    void toResponse_businessRuleException_returns422() {
        Response response = mapper.toResponse(
                new BusinessRuleException("wallet.insufficient_funds", "no funds"));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("wallet.insufficient_funds"));
    }

    @Test
    void toResponse_rateLimitException_returns429WithRetryAfter() {
        Response response = mapper.toResponse(new RateLimitException("slow down", 42L));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaderString("Retry-After")).isEqualTo("42");
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("ratelimit.exceeded"));
    }

    @Test
    void toResponse_circuitOpenException_returns503() {
        Response response = mapper.toResponse(new CircuitOpenException("advisor down"));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ErrorResponse.class, body ->
                assertThat(body.errorKey()).isEqualTo("advisor.circuit_open"));
    }

    @Test
    void errorResponse_omitsDetailsField_whenNotSet() {
        ErrorResponse body = new ErrorResponse("user.email_taken", "Email is already registered");

        assertThat(body.details()).isNull();
    }
}
