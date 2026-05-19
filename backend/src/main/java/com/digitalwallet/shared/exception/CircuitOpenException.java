package com.digitalwallet.shared.exception;

/**
 * HTTP 503; errorKey {@code advisor.circuit_open} — NFR8 LLM isolation.
 * See docs/business-rules/ai-advisor-rules.md (Cross-cutting on circuit breaker).
 */
public final class CircuitOpenException extends DomainException {
    public CircuitOpenException() {
        super("advisor.circuit_open", "Advisor unavailable, try later");
    }
}
