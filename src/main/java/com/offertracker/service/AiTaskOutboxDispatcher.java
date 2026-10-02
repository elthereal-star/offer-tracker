package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Profile("production")
public class AiTaskOutboxDispatcher {
    private final AiTaskMapper tasks;
    private final AiTaskQueue queue;
    private final Duration retryDelay;

    public AiTaskOutboxDispatcher(AiTaskMapper tasks, AiTaskQueue queue,
                                  @Value("${offer-tracker.ai.task-dispatch-retry-delay:1m}") Duration retryDelay) {
        this.tasks = tasks; this.queue = queue; this.retryDelay = retryDelay;
    }

    @Scheduled(fixedDelayString = "${offer-tracker.ai.task-dispatch-delay:1000}")
    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retryBefore = now.minus(retryDelay);
        tasks.selectList(new LambdaQueryWrapper<AiTask>()
                        .eq(AiTask::getStatus, "PENDING")
                        .le(AiTask::getAvailableAt, now)
                        .and(q -> q.eq(AiTask::getDispatchStatus, "NEW")
                                .or().in(AiTask::getDispatchStatus, "PUBLISHED", "DISPATCHING")
                                .lt(AiTask::getUpdatedAt, retryBefore))
                        .orderByAsc(AiTask::getCreatedAt)
                        .last("LIMIT 100"))
                .forEach(task -> dispatch(task, now, retryBefore));
    }

    private void dispatch(AiTask task, LocalDateTime now, LocalDateTime retryBefore) {
        AiTask claiming = new AiTask(); claiming.setDispatchStatus("DISPATCHING"); claiming.setUpdatedAt(now);
        int claimed = tasks.update(claiming, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .eq(AiTask::getStatus, "PENDING")
                .le(AiTask::getAvailableAt, now)
                .and(q -> q.eq(AiTask::getDispatchStatus, "NEW")
                        .or().in(AiTask::getDispatchStatus, "PUBLISHED", "DISPATCHING")
                        .lt(AiTask::getUpdatedAt, retryBefore)));
        if (claimed == 0) return;

        try {
            queue.publish(task);
            AiTask published = new AiTask(); published.setDispatchStatus("PUBLISHED"); published.setUpdatedAt(LocalDateTime.now());
            tasks.update(published, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .eq(AiTask::getStatus, "PENDING")
                    .eq(AiTask::getDispatchStatus, "DISPATCHING"));
        } catch (RuntimeException ex) {
            AiTask retry = new AiTask(); retry.setDispatchStatus("NEW"); retry.setUpdatedAt(LocalDateTime.now());
            tasks.update(retry, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .eq(AiTask::getStatus, "PENDING")
                    .eq(AiTask::getDispatchStatus, "DISPATCHING"));
            throw ex;
        }
    }
}
