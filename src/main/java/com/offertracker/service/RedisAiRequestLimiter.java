package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("production")
public class RedisAiRequestLimiter implements AiRequestLimiter {
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
            return count
            """, Long.class);
    private static final int WINDOW_SECONDS = 60;

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RedisAiRequestLimiter(StringRedisTemplate redis,
                                 @Value("${offer-tracker.ai.requests-per-minute:10}") int requestsPerMinute) {
        if (requestsPerMinute < 1) throw new IllegalArgumentException("AI request limit must be positive");
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public void checkAllowed(Long userId) {
        if (userId == null) throw new BusinessException(401, "AI 面试功能需要登录账户");
        String key = "offer-tracker:rate:ai:{" + userId + "}:minute";
        Long count = redis.execute(CONSUME_SCRIPT, List.of(key), Integer.toString(WINDOW_SECONDS));
        if (count == null || count > requestsPerMinute) {
            throw new BusinessException(429, "AI 请求过于频繁，请 1 分钟后再试");
        }
    }
}
