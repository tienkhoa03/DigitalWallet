package com.digitalwallet.shared.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the status-code mapping table in .claude/rules/backend_coding.md §8.
 * Each typed exception MUST map to the canonical HTTP status; {@link RateLimitException}
 * MUST attach the {@code Retry-After} header per security.md §8.
 */
class DomainExceptionMapperTest {

    private final DomainExceptionMapper mapper = new DomainExceptionMapper();

    @Test
    void validation_exception_maps_to_400() {
        Response r = mapper.toResponse(ValidationException.invalidPayload("bad"));
        assertThat(r.getStatus()).isEqualTo(400);
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertThat(body.errorKey()).isEqualTo("validation.invalid_payload");
    }

    @Test
    void idempotency_key_required_maps_to_400() {
        Response r = mapper.toResponse(new IdempotencyKeyRequiredException());
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(((ApiErrorResponse) r.getEntity()).errorKey()).isEqualTo("idempotency.key_required");
    }

    @Test
    void auth_invalid_credentials_maps_to_401() {
        Response r = mapper.toResponse(new AuthInvalidCredentialsException());
        assertThat(r.getStatus()).isEqualTo(401);
    }

    @Test
    void auth_forbidden_maps_to_403() {
        Response r = mapper.toResponse(new AuthForbiddenException());
        assertThat(r.getStatus()).isEqualTo(403);
    }

    @Test
    void account_suspended_maps_to_403() {
        Response r = mapper.toResponse(new AccountSuspendedException());
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(((ApiErrorResponse) r.getEntity()).errorKey()).isEqualTo("account.suspended");
    }

    @Test
    void conflict_maps_to_409() {
        Response r = mapper.toResponse(new ConflictException("wallet.locked", "busy"));
        assertThat(r.getStatus()).isEqualTo(409);
    }

    @Test
    void business_rule_maps_to_422() {
        Response r = mapper.toResponse(new BusinessRuleException("wallet.insufficient_funds", "low"));
        assertThat(r.getStatus()).isEqualTo(422);
    }

    @Test
    void fraud_velocity_exceeded_uses_422_business_rule_envelope() {
        Response r = mapper.toResponse(new BusinessRuleException("fraud.velocity_exceeded", "too fast"));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(((ApiErrorResponse) r.getEntity()).errorKey()).isEqualTo("fraud.velocity_exceeded");
    }

    @Test
    void rate_limit_attaches_retry_after_header() {
        Response r = mapper.toResponse(new RateLimitException(42L));
        assertThat(r.getStatus()).isEqualTo(429);
        assertThat(r.getHeaderString("Retry-After")).isEqualTo("42");
    }

    @Test
    void circuit_open_maps_to_503() {
        Response r = mapper.toResponse(new CircuitOpenException());
        assertThat(r.getStatus()).isEqualTo(503);
        assertThat(((ApiErrorResponse) r.getEntity()).errorKey()).isEqualTo("advisor.circuit_open");
    }

    @Test
    void audit_failure_maps_to_500() {
        Response r = mapper.toResponse(new AuditFailureException("oops", new RuntimeException()));
        assertThat(r.getStatus()).isEqualTo(500);
    }
}
