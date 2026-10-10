package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/** Redis lease lock for multi-instance deployments. The token prevents unlocking another owner's lease. */
@Service
@Profile("redis")
public class RedisAiSessionLock implements AiSessionLock {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final AiSessionFence fence;

    public RedisAiSessionLock(StringRedisTemplate redis, AiSessionFence fence) { this.redis = redis; this.fence = fence; }

    @Override public <T> T execute(Long sessionId, Supplier<T> operation) {
        return withLease(sessionId, () -> operation.get());
    }

    @Override public <T> T executeFenced(Long sessionId, LongFunction<T> operation) {
        return withLease(sessionId, () -> operation.apply(fence.issue(sessionId)));
    }

    /** 抢到租约后才发号，保证“令牌大小”与“持锁先后”一致。 */
    private <T> T withLease(Long sessionId, Supplier<T> operation) {
        String key = "offer:ai:lock:" + sessionId, token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, LEASE))) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("AI 面试会话操作繁忙，请稍后重试");
            try { Thread.sleep(25); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("获取 AI 面试锁被中断", ex); }
        }
        try { return operation.get(); }
        finally { redis.execute(RELEASE, java.util.List.of(key), token); }
    }
}
