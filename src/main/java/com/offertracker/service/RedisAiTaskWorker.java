package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

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
    private static final String DEAD_LETTER_STREAM = "offer-tracker:ai-tasks:dead-letter";
    private static final int MAX_ATTEMPTS = 3;
    private static final int LEASE_RECOVERY_BATCH_SIZE = 100;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;
    private final AiTaskMapper tasks;
    private final AiInterviewService interviews;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final String consumer = "worker-" + UUID.randomUUID();
    private volatile boolean groupReady;

    public RedisAiTaskWorker(StringRedisTemplate redis, AiTaskMapper tasks, AiInterviewService interviews, ObjectMapper objectMapper, MeterRegistry metrics) {
        this.redis = redis; this.tasks = tasks; this.interviews = interviews; this.objectMapper = objectMapper; this.metrics = metrics;
    }

    @PostConstruct
    void initializeGroup() {
        ensureGroup();
    }

    boolean ensureGroup() {
        if (groupReady) return true;
        try {
            redis.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    redis.getStringSerializer().serialize(STREAM), GROUP, ReadOffset.latest(), true));
            groupReady = true;
        } catch (Exception ex) {
            if (isExistingGroup(ex)) {
                groupReady = true;
            } else {
                log.debug("AI task consumer group is not ready; initialization will be retried", ex);
            }
        }
        return groupReady;
    }

    @Scheduled(fixedDelayString = "${offer-tracker.ai.task-poll-delay:1000}")
    void poll() {
        if (!ensureGroup()) return;
        recoverExpiredLeases();
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(GROUP, consumer),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(500)),
                org.springframework.data.redis.connection.stream.StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        if (records == null) return;
        for (MapRecord<String, Object, Object> record : records) process(record);
    }

    @Scheduled(fixedDelayString = "${offer-tracker.ai.task-lease-scan-delay:30000}")
    void recoverExpiredLeases() {
        List<AiTask> expired = tasks.selectList(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, "PROCESSING")
                .lt(AiTask::getLeaseUntil, LocalDateTime.now())
                .orderByAsc(AiTask::getLeaseUntil)
                .last("LIMIT " + LEASE_RECOVERY_BATCH_SIZE));
        for (AiTask task : expired) {
            boolean exhausted = task.getAttempts() >= MAX_ATTEMPTS;
            AiTask update = new AiTask();
            update.setStatus(exhausted ? "FAILED" : "PENDING");
            update.setDispatchStatus(exhausted ? "PUBLISHED" : "NEW");
            update.setLeaseUntil(null); update.setUpdatedAt(LocalDateTime.now());
            int updated = tasks.update(update, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .eq(AiTask::getStatus, "PROCESSING")
                    .set(AiTask::getLeaseUntil, null)
                    .lt(AiTask::getLeaseUntil, LocalDateTime.now()));
            if (updated == 0) continue;
            task.setStatus(update.getStatus());
            if (exhausted) { publishDeadLetter(task); metrics.counter("offer_tracker_ai_tasks_dead_letter_total").increment(); }
            metrics.counter("offer_tracker_ai_tasks_lease_recovered_total").increment();
        }
    }

    void process(MapRecord<String, Object, Object> record) {
        Object rawId = record.getValue().get("taskId");
        if (rawId == null) { acknowledge(record); return; }
        AiTask task = tasks.selectById(Long.valueOf(rawId.toString()));
        if (task == null || !claim(task)) { acknowledge(record); return; }
        task.setStatus("PROCESSING");
        task.setDispatchStatus("PUBLISHED");
        task.setAttempts(task.getAttempts() + 1);
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
            task.setStatus("SUCCEEDED"); task.setDispatchStatus("PUBLISHED"); task.setResult(objectMapper.writeValueAsString(result)); task.setErrorMessage(null); task.setLeaseUntil(null);
            task.setUpdatedAt(LocalDateTime.now()); updateAndClearLease(task);
            metrics.counter("offer_tracker_ai_tasks_succeeded_total").increment();
        } catch (Exception ex) {
            task.setUpdatedAt(LocalDateTime.now());
            task.setErrorMessage(safeErrorMessage(ex));
            log.warn("AI task failed taskId={} attempt={} errorType={}",
                    task.getId(), task.getAttempts(), ex.getClass().getSimpleName());
            task.setStatus(task.getAttempts() >= MAX_ATTEMPTS ? "FAILED" : "PENDING");
            task.setDispatchStatus("PENDING".equals(task.getStatus()) ? "NEW" : "PUBLISHED");
            task.setLeaseUntil(null);
            updateAndClearLease(task);
            if ("PENDING".equals(task.getStatus())) metrics.counter("offer_tracker_ai_tasks_retried_total").increment();
            else { publishDeadLetter(task); metrics.counter("offer_tracker_ai_tasks_dead_letter_total").increment(); }
        } finally { CurrentUserContext.clear(); acknowledge(record); }
    }

    boolean claim(AiTask task) {
        if (!"PENDING".equals(task.getStatus()) || task.getAvailableAt() != null && task.getAvailableAt().isAfter(LocalDateTime.now())) return false;
        AiTask update = new AiTask(); update.setStatus("PROCESSING"); update.setDispatchStatus("PUBLISHED"); update.setAttempts(task.getAttempts() + 1); update.setLeaseUntil(LocalDateTime.now().plus(LEASE)); update.setUpdatedAt(LocalDateTime.now());
        return tasks.update(update, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .eq(AiTask::getStatus, "PENDING")) == 1;
    }

    private void publishDeadLetter(AiTask task) {
        redis.opsForStream().add(org.springframework.data.redis.connection.stream.StreamRecords.newRecord().in(DEAD_LETTER_STREAM).ofMap(
                java.util.Map.of("taskId", task.getId().toString(), "taskType", task.getTaskType(), "error", task.getErrorMessage() == null ? "lease expired" : task.getErrorMessage())));
    }

    private void updateAndClearLease(AiTask task) {
        tasks.update(task, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .set(AiTask::getLeaseUntil, null));
    }

    static String safeErrorMessage(Exception error) {
        if (!(error instanceof BusinessException business)) return "AI 任务执行失败，请稍后重试";
        String message = business.getMessage();
        if (message == null || message.isBlank()) return "AI 任务执行失败，请稍后重试";
        int codePoints = message.codePointCount(0, message.length());
        if (codePoints <= 1024) return message;
        return message.substring(0, message.offsetByCodePoints(0, 1024));
    }

    private Long optionalLong(JsonNode payload, String name) { return payload.hasNonNull(name) ? payload.get(name).asLong() : null; }
    private void acknowledge(MapRecord<String, Object, Object> record) { redis.opsForStream().acknowledge(STREAM, GROUP, record.getId()); }

    private boolean isExistingGroup(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) return true;
        }
        return false;
    }
}
