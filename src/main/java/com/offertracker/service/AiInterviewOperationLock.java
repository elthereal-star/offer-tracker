package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

/** Local fallback for session finalization; production can be replaced by a Redis lock adapter. */
@Service
@Profile("!redis")
public class AiInterviewOperationLock implements AiSessionLock {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AiSessionFence fence;

    public AiInterviewOperationLock(AiSessionFence fence) { this.fence = fence; }

    public ReentrantLock acquire(Long sessionId) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, ignored -> new ReentrantLock());
        lock.lock(); return lock;
    }
    public void release(Long sessionId, ReentrantLock lock) {
        lock.unlock(); if (!lock.hasQueuedThreads()) locks.remove(sessionId, lock);
    }
    @Override public <T> T execute(Long sessionId, java.util.function.Supplier<T> operation) {
        ReentrantLock lock = acquire(sessionId);
        try { return operation.get(); } finally { release(sessionId, lock); }
    }
    @Override public <T> T executeFenced(Long sessionId, LongFunction<T> operation) {
        ReentrantLock lock = acquire(sessionId);
        try { return operation.apply(fence.issue(sessionId)); } finally { release(sessionId, lock); }
    }
}
