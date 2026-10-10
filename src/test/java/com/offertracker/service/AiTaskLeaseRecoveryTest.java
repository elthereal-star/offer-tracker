package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.entity.AiTask;
import com.offertracker.entity.User;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.mapper.UserMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Transactional
class AiTaskLeaseRecoveryTest {
    @Autowired AiTaskMapper tasks;
    @Autowired UserMapper users;

    @Test
    void recoversExpiredWorkerLeasesInBoundedBatches() {
        User user = new User();
        user.setPhone("+8613912345678");
        user.setPasswordHash("unused");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);

        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(1);
        for (int i = 0; i < 101; i++) {
            AiTask task = new AiTask();
            task.setOwnerId(user.getId());
            task.setTaskType("GENERATE_QUESTION");
            task.setPayload("{}");
            task.setStatus("PROCESSING");
            task.setDispatchStatus("PUBLISHED");
            task.setIdempotencyKey("lease-recovery-" + i);
            task.setAttempts(1);
            task.setAvailableAt(expiredAt);
            task.setLeaseUntil(expiredAt);
            task.setCreatedAt(expiredAt);
            task.setUpdatedAt(expiredAt);
            tasks.insert(task);
        }

        RedisAiTaskWorker worker = new RedisAiTaskWorker(mock(StringRedisTemplate.class), tasks,
                mock(AiInterviewService.class), new ObjectMapper(), new SimpleMeterRegistry());
        worker.recoverExpiredLeases();

        assertEquals(100L, count(user.getId(), "PENDING"));
        assertEquals(1L, count(user.getId(), "PROCESSING"));

        worker.recoverExpiredLeases();
        assertEquals(101L, count(user.getId(), "PENDING"));
        assertEquals(0L, count(user.getId(), "PROCESSING"));
    }

    private long count(Long ownerId, String status) {
        return tasks.selectCount(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getOwnerId, ownerId).eq(AiTask::getStatus, status));
    }
}
