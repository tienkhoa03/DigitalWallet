package com.digitalwallet.shared.ratelimit;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

/**
 * Fixed-window token bucket backed by Redis INCR / EXPIRE.
 * Pattern: first INCR seeds the key with TTL = window; subsequent INCRs check against capacity.
 * Matches security.md §8 "server policy is fixed-window token bucket".
 */
@ApplicationScoped
public class RedisTokenBucket implements TokenBucket {

    private final ValueCommands<String, Long> values;
    private final KeyCommands<String> keys;

    public RedisTokenBucket(RedisDataSource ds) {
        this.values = ds.value(String.class, Long.class);
        this.keys = ds.key(String.class);
    }

    @Override
    public Decision tryConsume(String key, int capacity, Duration window) {
        long count = values.incr(key);
        if (count == 1L) {
            keys.expire(key, window.toSeconds());
        }
        if (count <= capacity) {
            return new Decision(true, 0L);
        }
        long ttl = keys.ttl(key);
        return new Decision(false, Math.max(1L, ttl));
    }
}
