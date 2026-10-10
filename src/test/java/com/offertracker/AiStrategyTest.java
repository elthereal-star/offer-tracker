package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.enums.AiInterviewStatus;
import com.offertracker.service.AiInterviewStateMachine;
import com.offertracker.service.AiSingleFlightService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiStrategyTest {
    @Test
    void singleFlightSharesOneInFlightCall() throws Exception {
        AiSingleFlightService service = new AiSingleFlightService(new SimpleMeterRegistry());
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.execute("same", () -> {
                calls.incrementAndGet(); entered.countDown();
                await(release);
                return "answer";
            }), pool);
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> service.execute("same", () -> {
                calls.incrementAndGet(); return "wrong";
            }), pool);
            // Give the follower a scheduling opportunity while the leader is still blocked.
            Thread.sleep(100);
            release.countDown();
            assertEquals("answer", first.get());
            assertEquals("answer", second.get());
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void stateMachineRejectsCompletedSessionMutation() {
        AiInterviewStateMachine stateMachine = new AiInterviewStateMachine();
        stateMachine.requireTransition("ACTIVE", AiInterviewStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> stateMachine.requireActive("COMPLETED"));
        assertThrows(BusinessException.class, () -> stateMachine.requireTransition("COMPLETED", AiInterviewStatus.ACTIVE));
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }
}
