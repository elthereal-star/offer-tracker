package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 基于 Redis INCR 的跨实例 fencing 发号器，保证多实例下令牌全局有序。 */
@Service
@Profile("redis")
public class RedisAiSessionFence implements AiSessionFence {

    private static final String KEY_PREFIX = "offer:ai:fence:";

    private final StringRedisTemplate redis;

    public RedisAiSessionFence(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long issue(Long sessionId) {
        if (sessionId == null) {
            return 0L;
        }
        Long value = redis.opsForValue().increment(KEY_PREFIX + sessionId);
        return value == null ? 0L : value;
    }

    @Override
    public boolean isCurrent(Long sessionId, long token) {
        if (sessionId == null) {
            return false;
        }
        String current = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (current == null) {
            return false;
        }
        try {
            return Long.parseLong(current) == token;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
