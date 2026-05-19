package com.digitalwallet.shared.time;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * Default {@link Clock} producer — UTC.
 * Per .claude/rules/upgrade-policy.md §3 ("Clock injection") services accept a {@link Clock}
 * rather than calling {@code Instant.now()} directly. Tests can override this with a
 * {@code @Mock @Produces} bean for a fixed-time clock.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @Singleton
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
