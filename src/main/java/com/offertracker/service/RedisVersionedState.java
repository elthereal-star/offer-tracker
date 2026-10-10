package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** Atomic Redis versioned state update. The Lua compare-and-set prevents stale workers overwriting newer state. */
@Service
@Profile("redis")
public class RedisVersionedState {
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>("local current = redis.call('get', KEYS[1]); if current and current ~= ARGV[1] then return 0 end; redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1", Long.class);
    private final StringRedisTemplate redis;
    public RedisVersionedState(StringRedisTemplate redis) { this.redis = redis; }
    public boolean compareAndSet(String key, long expectedVersion, String state, Duration ttl) {
        return Long.valueOf(1L).equals(redis.execute(CAS, List.of(key), String.valueOf(expectedVersion), state, String.valueOf(Math.max(1, ttl.toSeconds()))));
    }
}
