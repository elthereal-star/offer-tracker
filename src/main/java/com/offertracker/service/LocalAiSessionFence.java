package com.offertracker.service;

import com.offertracker.mapper.AiInterviewSessionMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单实例 fencing 发号器。
 *
 * <p>计数器本身只活在当前 JVM 里，所以进程重启后会归零；而库里的 {@code fence_token}
 * 是持久化的。若不对齐，重启后发出 1 而库里已经是 5，条件写入 {@code fence_token < 1}
 * 恒不成立，该会话的收尾会被永久拒绝。因此每个会话的首次发号会先读一次库中值作为下限。</p>
 */
@Service
@Profile("!redis")
public class LocalAiSessionFence implements AiSessionFence {

    private final AiInterviewSessionMapper sessions;
    private final ConcurrentHashMap<Long, AtomicLong> counters = new ConcurrentHashMap<>();

    public LocalAiSessionFence(AiInterviewSessionMapper sessions) {
        this.sessions = sessions;
    }

    @Override
    public long issue(Long sessionId) {
        if (sessionId == null) {
            return 0L;
        }
        AtomicLong counter = counters.computeIfAbsent(sessionId, ignored -> new AtomicLong());
        // 0 表示这个会话在本进程里还没发过号，此时先与库中对齐。
        // 用 compareAndSet 而不是直接 set：并发进来时只有一个线程能把下限写进去，
        // 之后各自 incrementAndGet 拿到的仍然是互不相同、且都大于下限的令牌。
        if (counter.get() == 0L) {
            counter.compareAndSet(0L, storedFenceToken(sessionId));
        }
        return counter.incrementAndGet();
    }

    private long storedFenceToken(Long sessionId) {
        Long stored = sessions.selectFenceToken(sessionId);
        return stored == null ? 0L : Math.max(0L, stored);
    }
}
