package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("production")
public class AiTaskOutboxDispatcher {
    private final AiTaskMapper tasks;
    private final AiTaskQueue queue;

    public AiTaskOutboxDispatcher(AiTaskMapper tasks, AiTaskQueue queue) { this.tasks = tasks; this.queue = queue; }

    @Scheduled(fixedDelayString = "${offer-tracker.ai.task-dispatch-delay:1000}")
    public void dispatchPending() {
        tasks.selectList(new LambdaQueryWrapper<AiTask>()
                        .eq(AiTask::getDispatchStatus, "NEW")
                        .eq(AiTask::getStatus, "PENDING")
                        .orderByAsc(AiTask::getCreatedAt)
                        .last("LIMIT 100"))
                .forEach(this::dispatch);
    }

    private void dispatch(AiTask task) {
        queue.publish(task);
        AiTask update = new AiTask(); update.setDispatchStatus("PUBLISHED"); update.setUpdatedAt(LocalDateTime.now());
        tasks.update(update, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId()).eq(AiTask::getDispatchStatus, "NEW")
                .eq(AiTask::getStatus, "PENDING"));
    }
}
