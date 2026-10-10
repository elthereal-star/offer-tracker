package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("redis")
public class RedisDeduplicationFilter implements DeduplicationFilter {
    private final StringRedisTemplate redis;
    public RedisDeduplicationFilter(StringRedisTemplate redis) { this.redis = redis; }
    @Override public boolean mightContain(String key) { return Boolean.TRUE.equals(redis.opsForSet().isMember("offer:dedup", key)); }
    @Override public void put(String key) { redis.opsForSet().add("offer:dedup", key); }
}
