package com.digitalwallet.shared.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis SET NX EX implementation of the outer lock in the hybrid concurrency strategy (NFR1).
 * The lock value is a per-acquisition UUID so release is guarded by ownership.
 *
 * <p>This is intentionally NOT a "Redlock multi-node" implementation — the spec mandates a single
 * Redis instance in MVP (project-info.md §4.3).
 */
@ApplicationScoped
public class RedisDistributedLock implements DistributedLock {

    private static final Logger LOG = Logger.getLogger(RedisDistributedLock.class);

    private final ValueCommands<String, String> values;

    public RedisDistributedLock(RedisDataSource ds) {
        this.values = ds.value(String.class, String.class);
    }

    @Override
    public <T> T withLock(String key, Duration ttl, Duration waitFor, LockedAction<T> action) {
        String token = UUID.randomUUID().toString();
        if (!acquire(key, token, ttl, waitFor)) {
            throw new LockUnavailableException(key);
        }
        try {
            return action.run();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            release(key, token);
        }
    }

    private boolean acquire(String key, String token, Duration ttl, Duration waitFor) {
        long deadline = System.nanoTime() + waitFor.toNanos();
        long sleepNanos = Duration.ofMillis(10).toNanos();
        while (true) {
            SetArgs args = new SetArgs().nx().ex(Math.max(1, ttl.toSeconds()));
            values.set(key, token, args);
            String current = values.get(key);
            if (token.equals(current)) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(sleepNanos / 1_000_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void release(String key, String token) {
        String current = values.get(key);
        if (token.equals(current)) {
            values.getdel(key);
        } else {
            LOG.warnf("Lock %s already released or held by another holder (token mismatch)", key);
        }
    }
}
