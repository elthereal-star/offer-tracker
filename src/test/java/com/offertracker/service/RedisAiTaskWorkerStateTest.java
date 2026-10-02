package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAiTaskWorkerStateTest {

    @Test
    void claimAtomicallyIncrementsAttemptAndSetsLease() {
        AiTaskMapper tasks = mock(AiTaskMapper.class);
        when(tasks.update(any(AiTask.class), any(Wrapper.class))).thenReturn(1);
        RedisAiTaskWorker worker = worker(tasks);
        AiTask task = pendingTask(2);

        assertTrue(worker.claim(task));

        ArgumentCaptor<AiTask> update = ArgumentCaptor.forClass(AiTask.class);
        verify(tasks).update(update.capture(), any(Wrapper.class));
        assertEquals("PROCESSING", update.getValue().getStatus());
        assertEquals("PUBLISHED", update.getValue().getDispatchStatus());
        assertEquals(3, update.getValue().getAttempts());
        assertTrue(update.getValue().getLeaseUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void claimRejectsNonPendingAndNotYetAvailableTasks() {
        AiTaskMapper tasks = mock(AiTaskMapper.class);
        RedisAiTaskWorker worker = worker(tasks);
        AiTask processing = pendingTask(0);
        processing.setStatus("PROCESSING");
        AiTask scheduled = pendingTask(0);
        scheduled.setAvailableAt(LocalDateTime.now().plusMinutes(1));

        assertFalse(worker.claim(processing));
        assertFalse(worker.claim(scheduled));
        org.mockito.Mockito.verifyNoInteractions(tasks);
    }

    private RedisAiTaskWorker worker(AiTaskMapper tasks) {
        return new RedisAiTaskWorker(mock(StringRedisTemplate.class), tasks,
                mock(AiInterviewService.class), new ObjectMapper(), new SimpleMeterRegistry());
    }

    private AiTask pendingTask(int attempts) {
        AiTask task = new AiTask();
        task.setId(5L);
        task.setTaskType("GENERATE_QUESTION");
        task.setOwnerId(1L);
        task.setStatus("PENDING");
        task.setDispatchStatus("PUBLISHED");
        task.setAttempts(attempts);
        task.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}
