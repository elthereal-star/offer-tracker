package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.dto.AiInterviewSessionResponse;
import com.offertracker.dto.CreateAiInterviewRequest;
import com.offertracker.entity.AiTask;
import com.offertracker.entity.User;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.mapper.UserMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AiTaskOutboxRedisIntegrationTest {
    private static final AtomicInteger IDS = new AtomicInteger();
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @Autowired AiTaskMapper tasks;
    @Autowired UserMapper users;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry metrics;

    @Test
    void republishesPendingTaskAfterRedisStreamIsLost() {
        redis.delete(RedisAiTaskQueue.STREAM);
        User user = user();
        AiTask task = task(user);
        RedisAiTaskQueue queue = new RedisAiTaskQueue(redis, metrics);
        AiTaskOutboxDispatcher dispatcher = new AiTaskOutboxDispatcher(tasks, queue, Duration.ofMinutes(1));

        dispatcher.dispatchPending();
        assertEquals("PUBLISHED", tasks.selectById(task.getId()).getDispatchStatus());
        assertEquals(1, readStream().size());

        redis.delete(RedisAiTaskQueue.STREAM);
        task = tasks.selectById(task.getId());
        task.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        tasks.updateById(task);

        dispatcher.dispatchPending();

        assertEquals("PUBLISHED", tasks.selectById(task.getId()).getDispatchStatus());
        List<MapRecord<String, Object, Object>> replayed = readStream();
        assertEquals(1, replayed.size());
        assertEquals(task.getId().toString(), replayed.get(0).getValue().get("taskId"));
    }

    @Test
    void createsConsumerGroupOnAnEmptyRedisStream() {
        redis.delete(RedisAiTaskQueue.STREAM);
        RedisAiTaskWorker worker = new RedisAiTaskWorker(redis, tasks,
                mock(AiInterviewService.class), new ObjectMapper(), metrics);

        worker.ensureGroup();

        var groups = redis.opsForStream().groups(RedisAiTaskQueue.STREAM);
        assertEquals(1, groups.size());
        assertEquals("offer-tracker-ai-workers", groups.get(0).groupName());
    }

    @Test
    void duplicateStreamMessagesExecuteTheAiTaskOnlyOnce() {
        redis.delete(RedisAiTaskQueue.STREAM);
        User user = user();
        AiTask task = task(user);
        task.setPayload("{\"resumeId\":7}");
        task.setDispatchStatus("PUBLISHED");
        tasks.updateById(task);

        AiInterviewService interviews = mock(AiInterviewService.class);
        when(interviews.create(any(CreateAiInterviewRequest.class))).thenReturn(
                new AiInterviewSessionResponse(99L, 7L, null, "ACTIVE", null, null, List.of()));
        RedisAiTaskWorker worker = new RedisAiTaskWorker(redis, tasks, interviews, new ObjectMapper(), metrics);
        worker.ensureGroup();
        Map<String, String> fields = Map.of("taskId", task.getId().toString(), "taskType", task.getTaskType());
        redis.opsForStream().add(StreamRecords.newRecord().in(RedisAiTaskQueue.STREAM).ofMap(fields));
        redis.opsForStream().add(StreamRecords.newRecord().in(RedisAiTaskQueue.STREAM).ofMap(fields));

        for (MapRecord<String, Object, Object> record : readStream()) worker.process(record);

        verify(interviews, times(1)).create(any(CreateAiInterviewRequest.class));
        AiTask completed = tasks.selectById(task.getId());
        assertEquals("SUCCEEDED", completed.getStatus());
        assertEquals(1, completed.getAttempts());
    }

    private List<MapRecord<String, Object, Object>> readStream() {
        return redis.opsForStream().read(StreamOffset.fromStart(RedisAiTaskQueue.STREAM));
    }

    private AiTask task(User user) {
        AiTask task = new AiTask();
        task.setOwnerId(user.getId());
        task.setTaskType("GENERATE_QUESTION");
        task.setPayload("{}");
        task.setStatus("PENDING");
        task.setDispatchStatus("NEW");
        task.setIdempotencyKey("outbox-redis-loss-test-" + IDS.incrementAndGet());
        task.setAttempts(0);
        task.setAvailableAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        tasks.insert(task);
        return task;
    }

    private User user() {
        User user = new User();
        user.setPhone("+861391" + String.format("%06d", IDS.incrementAndGet()));
        user.setPasswordHash("unused");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }
}
