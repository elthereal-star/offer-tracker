package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.CreateAiInterviewRequest;
import com.offertracker.dto.SubmitAiInterviewAnswerRequest;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Profile("production")
public class RedisAiTaskWorker {
    private static final Logger log = LoggerFactory.getLogger(RedisAiTaskWorker.class);
    private static final String STREAM = RedisAiTaskQueue.STREAM;
    private static final String GROUP = "offer-tracker-ai-workers";
    private static final int MAX_ATTEMPTS = 3;
    private final StringRedisTemplate redis;
    private final AiTaskMapper tasks;
    private final AiInterviewService interviews;
    private final ObjectMapper objectMapper;
    private final String consumer = "worker-" + UUID.randomUUID();

    public RedisAiTaskWorker(StringRedisTemplate redis, AiTaskMapper tasks, AiInterviewService interviews, ObjectMapper objectMapper) {
        this.redis = redis; this.tasks = tasks; this.interviews = interviews; this.objectMapper = objectMapper;
    }

    @PostConstruct
    void ensureGroup() {
        try { redis.opsForStream().createGroup(STREAM, ReadOffset.latest(), GROUP); }
        catch (Exception ex) { log.debug("AI task consumer group already exists or stream is unavailable", ex); }
    }

    @Scheduled(fixedDelayString = "${offer-tracker.ai.task-poll-delay:1000}")
    void poll() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(GROUP, consumer),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(500)),
                org.springframework.data.redis.connection.stream.StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        if (records == null) return;
        for (MapRecord<String, Object, Object> record : records) process(record);
    }

    private void process(MapRecord<String, Object, Object> record) {
        Object rawId = record.getValue().get("taskId");
        if (rawId == null) { acknowledge(record); return; }
        AiTask task = tasks.selectById(Long.valueOf(rawId.toString()));
        if (task == null || !claim(task)) { acknowledge(record); return; }
        try {
            CurrentUserContext.set(new CurrentUser(task.getOwnerId(), "USER"));
            JsonNode payload = objectMapper.readTree(task.getPayload());
            Object result = switch (task.getTaskType()) {
                case "GENERATE_QUESTION" -> interviews.create(new CreateAiInterviewRequest(payload.path("resumeId").asLong(), optionalLong(payload, "applicationId")));
                case "EVALUATE_ANSWER" -> interviews.evaluate(payload.path("sessionId").asLong(), payload.path("questionId").asLong());
                case "FOLLOW_UP" -> interviews.followUp(payload.path("sessionId").asLong(), payload.path("questionId").asLong());
                case "FINISH_INTERVIEW" -> interviews.finish(payload.path("sessionId").asLong());
                default -> throw new IllegalArgumentException("unsupported AI task type: " + task.getTaskType());
            };
            task.setStatus("SUCCEEDED"); task.setResult(objectMapper.writeValueAsString(result)); task.setErrorMessage(null);
            task.setUpdatedAt(LocalDateTime.now()); tasks.updateById(task);
        } catch (Exception ex) {
            task.setAttempts(task.getAttempts() + 1); task.setUpdatedAt(LocalDateTime.now());
            task.setErrorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            task.setStatus(task.getAttempts() >= MAX_ATTEMPTS ? "FAILED" : "PENDING");
            tasks.updateById(task);
            if ("PENDING".equals(task.getStatus())) redis.opsForStream().add(org.springframework.data.redis.connection.stream.StreamRecords.newRecord().in(STREAM).ofMap(java.util.Map.of("taskId", task.getId().toString(), "taskType", task.getTaskType())));
        } finally { CurrentUserContext.clear(); acknowledge(record); }
    }

    private boolean claim(AiTask task) {
        if (!"PENDING".equals(task.getStatus()) || task.getAvailableAt() != null && task.getAvailableAt().isAfter(LocalDateTime.now())) return false;
        AiTask update = new AiTask(); update.setStatus("PROCESSING"); update.setAttempts(task.getAttempts() + 1); update.setUpdatedAt(LocalDateTime.now());
        return tasks.update(update, new LambdaUpdateWrapper<AiTask>().eq(AiTask::getId, task.getId()).eq(AiTask::getStatus, "PENDING")) == 1;
    }

    private Long optionalLong(JsonNode payload, String name) { return payload.hasNonNull(name) ? payload.get(name).asLong() : null; }
    private void acknowledge(MapRecord<String, Object, Object> record) { redis.opsForStream().acknowledge(STREAM, GROUP, record.getId()); }
}
