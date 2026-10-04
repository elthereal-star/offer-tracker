package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Monotonic turn numbers for distributed interview transports. */
public interface AiMessageSequenceService {
    long next(Long sessionId);

    @Service
    @Profile("redis")
    class Redis implements AiMessageSequenceService {
        private final StringRedisTemplate redis;
        public Redis(StringRedisTemplate redis) { this.redis = redis; }
        @Override public long next(Long sessionId) { return redis.opsForValue().increment("offer:ai:sequence:" + sessionId); }
    }

    @Service
    @Profile("!redis")
    class Local implements AiMessageSequenceService {
        private final ConcurrentHashMap<Long, AtomicLong> sequences = new ConcurrentHashMap<>();
        @Override public long next(Long sessionId) { return sequences.computeIfAbsent(sessionId, ignored -> new AtomicLong()).incrementAndGet(); }
    }
}
