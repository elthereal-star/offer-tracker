package com.offertracker;

import com.offertracker.entity.AiTask;
import com.offertracker.entity.User;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.AiTaskOutboxDispatcher;
import com.offertracker.service.AiTaskQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@Transactional
class AiTaskOutboxRecoveryTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Autowired AiTaskMapper tasks;
    @Autowired UserMapper users;

    @Test
    void requeuesStalePublishedAndDispatchingTasksButSkipsFreshOrUnavailableTasks() {
        User user = user();
        LocalDateTime now = LocalDateTime.now();
        AiTask newTask = task(user, "NEW", "PENDING", now, now);
        AiTask stalePublished = task(user, "PUBLISHED", "PENDING", now, now.minusMinutes(5));
        AiTask staleDispatching = task(user, "DISPATCHING", "PENDING", now, now.minusMinutes(5));
        AiTask freshPublished = task(user, "PUBLISHED", "PENDING", now, now);
        AiTask unavailable = task(user, "NEW", "PENDING", now.plusMinutes(5), now);
        task(user, "PUBLISHED", "PROCESSING", now, now.minusMinutes(5));

        AiTaskQueue queue = mock(AiTaskQueue.class);
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue, Duration.ofMinutes(1));
        dispatcher.dispatchPending();

        verify(queue).publish(org.mockito.ArgumentMatchers.argThat(task -> task.getId().equals(newTask.getId())));
        verify(queue).publish(org.mockito.ArgumentMatchers.argThat(task -> task.getId().equals(stalePublished.getId())));
        verify(queue).publish(org.mockito.ArgumentMatchers.argThat(task -> task.getId().equals(staleDispatching.getId())));
        verify(queue, times(3)).publish(org.mockito.ArgumentMatchers.any(AiTask.class));
        assertEquals("PUBLISHED", tasks.selectById(newTask.getId()).getDispatchStatus());
        assertEquals("PUBLISHED", tasks.selectById(stalePublished.getId()).getDispatchStatus());
        assertEquals("PUBLISHED", tasks.selectById(staleDispatching.getId()).getDispatchStatus());
        assertEquals("PUBLISHED", tasks.selectById(freshPublished.getId()).getDispatchStatus());
        assertEquals("NEW", tasks.selectById(unavailable.getId()).getDispatchStatus());
    }

    private AiTask task(User user, String dispatchStatus, String status, LocalDateTime availableAt, LocalDateTime updatedAt) {
        AiTask task = new AiTask();
        task.setOwnerId(user.getId());
        task.setTaskType("GENERATE_QUESTION");
        task.setPayload("{}");
        task.setStatus(status);
        task.setDispatchStatus(dispatchStatus);
        task.setIdempotencyKey("outbox-recovery-" + IDS.incrementAndGet());
        task.setAttempts(0);
        task.setAvailableAt(availableAt);
        task.setCreatedAt(updatedAt);
        task.setUpdatedAt(updatedAt);
        tasks.insert(task);
        return task;
    }

    private User user() {
        User user = new User();
        user.setPhone("+86139000" + String.format("%04d", IDS.incrementAndGet()));
        user.setPasswordHash("unused");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }
}
