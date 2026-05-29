package com.digitalwallet.shared.exception;

/**
 * 503 Service Unavailable — the SmallRye Fault-Tolerance circuit breaker on the
 * LLM advisor path is open (NFR8). No caller until Epic 6 (deferred MVP-wide);
 * shipped for the sealed {@code permits} list.
 */
public final class CircuitOpenException extends DomainException {

    public CircuitOpenException(String message) {
        super("advisor.circuit_open", message);
    }
}
