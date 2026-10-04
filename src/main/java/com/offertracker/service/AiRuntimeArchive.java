package com.offertracker.service;

/** Optional archive port for long-form AI conversations. The core profile uses MySQL snapshots. */
public interface AiRuntimeArchive {
    void append(Long sessionId, String stateJson, long version);
    default String latest(Long sessionId) { return null; }
}
