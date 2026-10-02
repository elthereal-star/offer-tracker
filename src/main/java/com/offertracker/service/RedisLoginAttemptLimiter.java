package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
@Profile("production")
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;

    public RedisLoginAttemptLimiter(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void checkAllowed(String phone) {
        String key = key(phone);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, WINDOW);
        if (count != null && count > MAX_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请 15 分钟后再试");
        }
    }

    @Override public void clear(String phone) { redis.delete(key(phone)); }

    private String key(String phone) { return "offer-tracker:rate:login:{" + sha256(phone.trim()) + "}"; }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
