package com.offertracker.service;

import java.util.function.Supplier;

/** Coordinates one mutating interview operation for a session. */
public interface AiSessionLock {
    <T> T execute(Long sessionId, Supplier<T> operation);
}
