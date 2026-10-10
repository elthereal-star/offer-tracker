package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 单实例 fencing 发号器；令牌只在当前 JVM 内有序。 */
@Service
@Profile("!redis")
public class LocalAiSessionFence implements AiSessionFence {

    private final ConcurrentHashMap<Long, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long issue(Long sessionId) {
        if (sessionId == null) {
            return 0L;
        }
        return counters.computeIfAbsent(sessionId, ignored -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public boolean isCurrent(Long sessionId, long token) {
        if (sessionId == null) {
            return false;
        }
        AtomicLong counter = counters.get(sessionId);
        return counter != null && counter.get() == token;
    }
}
