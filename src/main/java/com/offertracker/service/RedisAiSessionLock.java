package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Redis lease lock for multi-instance deployments. The token prevents unlocking another owner's lease. */
@Service
@Profile("redis")
public class RedisAiSessionLock implements AiSessionLock {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    public RedisAiSessionLock(StringRedisTemplate redis) { this.redis = redis; }
    @Override public <T> T execute(Long sessionId, Supplier<T> operation) {
        String key = "offer:ai:lock:" + sessionId, token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(30)))) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("AI 面试会话操作繁忙，请稍后重试");
            try { Thread.sleep(25); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("获取 AI 面试锁被中断", ex); }
        }
        try { return operation.get(); }
        finally { redis.execute(RELEASE, java.util.List.of(key), token); }
    }
}
