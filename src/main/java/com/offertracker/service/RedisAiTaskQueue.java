package com.offertracker.service;

import com.offertracker.entity.AiTask;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("production")
public class RedisAiTaskQueue implements AiTaskQueue {
    static final String STREAM = "offer-tracker:ai-tasks";
    private final StringRedisTemplate redis;

    public RedisAiTaskQueue(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void publish(AiTask task) {
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(STREAM)
                .ofMap(Map.of("taskId", task.getId().toString(), "taskType", task.getTaskType()));
        redis.opsForStream().add(record);
    }
}
