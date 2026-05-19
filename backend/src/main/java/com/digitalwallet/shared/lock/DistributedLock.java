package com.digitalwallet.shared.lock;

import java.time.Duration;

/**
 * Contract for the outer lock in the hybrid concurrency strategy (NFR1).
 * Acquisition order is fixed in backend_coding.md §3 — Redis lock → DB row lock → outbox → commit.
 * Implementations MUST fail fast: a failed acquisition raises {@link LockUnavailableException},
 * never blocks beyond the supplied timeout.
 */
public interface DistributedLock {

    /**
     * Acquire the lock and execute the action; release in a {@code finally} unconditionally.
     *
     * @param key      lock identity (e.g. {@code "wallet:" + walletId}).
     * @param ttl      max time the lock is held — protects against crashed holders.
     * @param waitFor  max time to wait for the lock before failing.
     * @param action   work performed while holding the lock.
     */
    <T> T withLock(String key, Duration ttl, Duration waitFor, LockedAction<T> action);

    @FunctionalInterface
    interface LockedAction<T> {
        T run() throws Exception;
    }
}
