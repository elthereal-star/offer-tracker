package com.offertracker.service;

import com.offertracker.mapper.AiInterviewSessionMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Redis 的跨实例 fencing 发号器，保证多实例下令牌全局有序。
 *
 * <p>Redis 的键同样可能丢失：未开持久化时重启、主从切换、键被逐出或人工 FLUSH，
 * 都会让计数器回到 0。因此发号脚本把库中已记录的令牌一起传进去作为下限，
 * 让「发出的令牌 &gt; 库中值」这个不变量不依赖 Redis 的持久性。</p>
 *
 * <p>代价是每次发号多一次主键查询。发号只发生在会话收尾这条低频路径上
 * （本身就要跑一次秒级的 AI 调用），这个开销可以忽略。</p>
 */
@Service
@Profile("redis")
public class RedisAiSessionFence implements AiSessionFence {

    private static final String KEY_PREFIX = "offer:ai:fence:";

    /**
     * 取 max(Redis 当前值, 库中值) 后再 +1 并写回。整个过程在一次 Lua 调用里完成，
     * 避免读—算—写之间被其他实例插队。
     */
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local stored = tonumber(ARGV[1])
            if stored > current then current = stored end
            current = current + 1
            redis.call('SET', KEYS[1], current)
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AiInterviewSessionMapper sessions;

    public RedisAiSessionFence(StringRedisTemplate redis, AiInterviewSessionMapper sessions) {
        this.redis = redis;
        this.sessions = sessions;
    }

    @Override
    public long issue(Long sessionId) {
        if (sessionId == null) {
            return 0L;
        }
        Long token = redis.execute(ISSUE, List.of(KEY_PREFIX + sessionId), Long.toString(storedFenceToken(sessionId)));
        return token == null ? 0L : token;
    }

    private long storedFenceToken(Long sessionId) {
        Long stored = sessions.selectFenceToken(sessionId);
        return stored == null ? 0L : Math.max(0L, stored);
    }
}
