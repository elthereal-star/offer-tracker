package com.offertracker.service;

import java.util.function.Supplier;

/** Shares an identical in-flight provider request. */
public interface AiDistributedSingleFlight {
    <T> T execute(String key, Supplier<T> supplier);
}
