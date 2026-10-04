package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Cross-instance single-flight for the String responses used by AI provider calls. */
@Service
@Profile("redis")
public class RedisAiSingleFlightService implements AiDistributedSingleFlight {
    private final StringRedisTemplate redis; private final MeterRegistry metrics; private final ObjectMapper mapper;
    public RedisAiSingleFlightService(StringRedisTemplate redis, MeterRegistry metrics, ObjectMapper mapper) { this.redis = redis; this.metrics = metrics; this.mapper = mapper; }
    @Override public <T> T execute(String key, Supplier<T> supplier) {
        if (key == null || key.isBlank()) return supplier.get();
        String lock = "offer:ai:flight:lock:" + key, result = "offer:ai:flight:result:" + key;
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(90)))) {
            metrics.counter("offer_tracker_ai_singleflight_miss_total").increment();
            try { T value = supplier.get(); redis.opsForValue().set(result, mapper.convertValue(value, String.class), Duration.ofSeconds(90)); return value; }
            finally { redis.delete(lock); }
        }
        metrics.counter("offer_tracker_ai_singleflight_hit_total").increment();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(95);
        String value;
        while ((value = redis.opsForValue().get(result)) == null) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("AI 请求协调超时，请重试");
            try { Thread.sleep(25); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("等待 AI 请求被中断", ex); }
        }
        @SuppressWarnings("unchecked") T cast = (T) value; return cast;
    }
}
