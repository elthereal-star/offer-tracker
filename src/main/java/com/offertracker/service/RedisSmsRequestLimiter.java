package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
@Profile("production")
public class RedisSmsRequestLimiter implements SmsRequestLimiter {
    private static final List<Window> WINDOWS = List.of(
            new Window("minute", Duration.ofMinutes(1), 1),
            new Window("hour", Duration.ofHours(1), 5),
            new Window("day", Duration.ofDays(1), 10));
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
              local current = tonumber(redis.call('GET', key) or '0')
              local limit = tonumber(ARGV[(i * 2) - 1])
              if current >= limit then return 0 end
            end
            for i, key in ipairs(KEYS) do
              local count = redis.call('INCR', key)
              if count == 1 then redis.call('EXPIRE', key, tonumber(ARGV[i * 2])) end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisSmsRequestLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void checkAllowed(String phone) {
        String digest = sha256(phone.trim());
        List<String> keys = WINDOWS.stream()
                .map(window -> "offer-tracker:rate:sms:{" + digest + "}:" + window.name())
                .toList();
        Object[] arguments = WINDOWS.stream()
                .flatMap(window -> java.util.stream.Stream.of(
                        Integer.toString(window.limit()), Long.toString(window.duration().toSeconds())))
                .toArray();
        Long accepted = redis.execute(CONSUME_SCRIPT, keys, arguments);
        if (!Long.valueOf(1).equals(accepted)) {
            throw new BusinessException(429, "验证码请求过于频繁，请稍后再试");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record Window(String name, Duration duration, int limit) {}
}
