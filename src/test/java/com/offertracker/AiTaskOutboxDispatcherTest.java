package com.offertracker;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.service.AiTaskOutboxDispatcher;
import com.offertracker.service.AiTaskQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue);

        dispatcher.dispatchPending();

        verify(queue).publish(task);
        ArgumentCaptor<AiTask> update = ArgumentCaptor.forClass(AiTask.class);
        verify(tasks).update(update.capture(), any(Wrapper.class));
        assertEquals("PUBLISHED", update.getValue().getDispatchStatus());
    }

    @Test
    void leavesTaskAvailableForRetryWhenQueueRejectsIt() {
        AiTaskMapper tasks = mock(AiTaskMapper.class);
        AiTaskQueue queue = mock(AiTaskQueue.class);
        AiTask task = pendingTask();
        when(tasks.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        doThrow(new IllegalStateException("Redis unavailable")).when(queue).publish(task);
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue);

        IllegalStateException error = assertThrows(IllegalStateException.class, dispatcher::dispatchPending);
        assertEquals("Redis unavailable", error.getMessage());

        verify(queue).publish(task);
        verify(tasks, org.mockito.Mockito.never()).update(any(AiTask.class), any(Wrapper.class));
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
