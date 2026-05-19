package com.digitalwallet.shared.ratelimit;

import java.time.Duration;

/**
 * Contract for the per-user rate-limit primitive backing
 * {@code POST /transfers} (10/min) and {@code POST /advisor/*} (5/hour) —
 * .claude/rules/security.md §8 and docs/architecture/README.md §7.
 */
public interface TokenBucket {

    /**
     * Try to consume one token from the bucket scoped to {@code key}.
     *
     * @param key       bucket identity, e.g. {@code "ratelimit:transfer:" + accountId}.
     * @param capacity  max tokens per window.
     * @param window    refill window — fixed-window semantics per security.md §8 ("server policy
     *                  is fixed-window token bucket").
     * @return a {@link Decision} whose {@link Decision#allowed()} is {@code true} when the
     *         request may proceed, otherwise {@link Decision#retryAfterSeconds()} reports the
     *         seconds remaining until the window resets.
     */
    Decision tryConsume(String key, int capacity, Duration window);

    record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
