package com.offertracker.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Shares an in-flight AI call for an identical key within one application instance. */
@Service
@Profile("!redis")
public class AiSingleFlightService implements AiDistributedSingleFlight {
    private final ConcurrentMap<String, CompletableFuture<Object>> flights = new ConcurrentHashMap<>();
    private final MeterRegistry metrics;

    public AiSingleFlightService(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    public <T> T execute(String key, Supplier<T> supplier) {
        if (key == null || key.isBlank()) return supplier.get();
        CompletableFuture<Object> candidate = new CompletableFuture<>();
        CompletableFuture<Object> existing = flights.putIfAbsent(key, candidate);
        if (existing == null) {
            metrics.counter("offer_tracker_ai_singleflight_miss_total").increment();
            try {
                T result = supplier.get();
                candidate.complete(result);
                return result;
            } catch (Throwable ex) {
                candidate.completeExceptionally(ex);
                throw ex;
            } finally {
                flights.remove(key, candidate);
            }
        }
        metrics.counter("offer_tracker_ai_singleflight_hit_total").increment();
        try {
            @SuppressWarnings("unchecked")
            T result = (T) existing.join();
            return result;
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
    }
}
