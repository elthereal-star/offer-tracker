package com.offertracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Compact Snowflake-style generator: timestamp, configured worker id, and per-millisecond sequence. */
@Service
public class SnowflakeIdGenerator implements DistributedIdGenerator {
    private static final long EPOCH = 1704067200000L;
    private final long workerId;
    private long lastMillis = -1L; private long sequence;
    public SnowflakeIdGenerator(@Value("${offer-tracker.id.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > 1023) throw new IllegalArgumentException("worker id must be between 0 and 1023");
        this.workerId = workerId;
    }
    @Override public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastMillis) throw new IllegalStateException("system clock moved backwards");
        if (now == lastMillis) { sequence = (sequence + 1) & 4095; if (sequence == 0) { do { now = System.currentTimeMillis(); } while (now <= lastMillis); } }
        else sequence = 0;
        lastMillis = now;
        return ((now - EPOCH) << 22) | (workerId << 12) | sequence;
    }
}
