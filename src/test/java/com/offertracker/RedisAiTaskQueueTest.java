package com.offertracker;

import com.offertracker.entity.AiTask;
import com.offertracker.service.RedisAiTaskQueue;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisAiTaskQueueTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void publishesTaskIdentityAndTypeToStream() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
            RedisAiTaskQueue queue = new RedisAiTaskQueue(redis, new SimpleMeterRegistry());
            AiTask task = new AiTask(); task.setId(41L); task.setTaskType("GENERATE_QUESTION");
            task.setCreatedAt(LocalDateTime.now());
            queue.publish(task);
            List<?> records = redis.opsForStream().read(org.springframework.data.redis.connection.stream.StreamOffset.fromStart(RedisAiTaskQueue.STREAM));
            assertEquals(1, records.size());
            assertTrue(records.get(0).toString().contains("taskId=41"));
        } finally { factory.destroy(); }
    }
}
