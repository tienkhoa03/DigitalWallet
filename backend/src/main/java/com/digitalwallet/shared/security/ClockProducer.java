package com.digitalwallet.shared.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Single CDI producer for the system {@link Clock}.
 *
 * <p>Every time-aware service injects {@link Clock} so tests can substitute a fixed clock —
 * see {@code .claude/rules/testing.md §2.2} and {@code .claude/rules/upgrade-policy.md §3}.
 * Direct calls to {@code Instant.now()} inside service code are a defect.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @ApplicationScoped
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
