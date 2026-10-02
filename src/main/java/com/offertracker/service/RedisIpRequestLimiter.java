package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Profile("production")
public class RedisIpRequestLimiter implements IpRequestLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current >= tonumber(ARGV[1]) then return 0 end
            local next = redis.call('INCR', KEYS[1])
            if next == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    public RedisIpRequestLimiter(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void checkAllowed(String ip) {
        Long accepted = redis.execute(SCRIPT, List.of("offer-tracker:rate:ip:auth:" + ip), "60", Long.toString(Duration.ofMinutes(1).toSeconds()));
        if (!Long.valueOf(1).equals(accepted)) throw new BusinessException(429, "请求过于频繁，请稍后再试");
    }
}
