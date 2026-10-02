package com.offertracker;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.service.AiTaskOutboxDispatcher;
import com.offertracker.service.AiTaskQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskOutboxDispatcherTest {

    @Test
    void marksTaskPublishedOnlyAfterQueueAcceptsIt() {
        AiTaskMapper tasks = mock(AiTaskMapper.class);
        AiTaskQueue queue = mock(AiTaskQueue.class);
        AiTask task = pendingTask();
        when(tasks.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(tasks.update(any(AiTask.class), any(Wrapper.class))).thenReturn(1);
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue, Duration.ofMinutes(1));

        dispatcher.dispatchPending();

        verify(queue).publish(task);
        ArgumentCaptor<AiTask> update = ArgumentCaptor.forClass(AiTask.class);
        verify(tasks, org.mockito.Mockito.times(2)).update(update.capture(), any(Wrapper.class));
        assertEquals("DISPATCHING", update.getAllValues().get(0).getDispatchStatus());
        assertEquals("PUBLISHED", update.getAllValues().get(1).getDispatchStatus());
    }

    @Test
    void leavesTaskAvailableForRetryWhenQueueRejectsIt() {
        AiTaskMapper tasks = mock(AiTaskMapper.class);
        AiTaskQueue queue = mock(AiTaskQueue.class);
        AiTask task = pendingTask();
        when(tasks.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(tasks.update(any(AiTask.class), any(Wrapper.class))).thenReturn(1);
        doThrow(new IllegalStateException("Redis unavailable")).when(queue).publish(task);
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue, Duration.ofMinutes(1));

        IllegalStateException error = assertThrows(IllegalStateException.class, dispatcher::dispatchPending);
        assertEquals("Redis unavailable", error.getMessage());

        verify(queue).publish(task);
        ArgumentCaptor<AiTask> update = ArgumentCaptor.forClass(AiTask.class);
        verify(tasks, org.mockito.Mockito.times(2)).update(update.capture(), any(Wrapper.class));
        assertEquals("DISPATCHING", update.getAllValues().get(0).getDispatchStatus());
        assertEquals("NEW", update.getAllValues().get(1).getDispatchStatus());
    }

    private AiTask pendingTask() {
        AiTask task = new AiTask();
        task.setId(42L);
        task.setTaskType("GENERATE_QUESTION");
        task.setStatus("PENDING");
        task.setDispatchStatus("NEW");
        return task;
    }
}
