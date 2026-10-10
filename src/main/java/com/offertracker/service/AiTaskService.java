package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.AiTaskResponse;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AiTaskService {
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            "GENERATE_QUESTION", "EVALUATE_ANSWER", "FOLLOW_UP", "FINISH_INTERVIEW", "REPAIR_TURN");
    private final AiTaskMapper tasks;
    private final ObjectMapper objectMapper;

    public AiTaskService(AiTaskMapper tasks, ObjectMapper objectMapper) {
        this.tasks = tasks; this.objectMapper = objectMapper;
    }

    @Transactional
    public AiTaskResponse submit(String taskType, String idempotencyKey, Object payload) {
        Long ownerId = requireOwner();
        if (taskType == null || taskType.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(400, "任务类型和幂等键不能为空");
        }
        String normalizedTaskType = taskType.trim();
        String normalizedIdempotencyKey = idempotencyKey.trim();
        if (!SUPPORTED_TASK_TYPES.contains(normalizedTaskType)) {
            throw new BusinessException(400, "不支持的 AI 任务类型");
        }
        if (normalizedIdempotencyKey.length() > 128) {
            throw new BusinessException(400, "幂等键长度不能超过 128 个字符");
        }
        String payloadJson = validateAndWritePayload(normalizedTaskType, payload);
        try {
            AiTask task = new AiTask();
            task.setOwnerId(ownerId); task.setTaskType(normalizedTaskType);
            task.setIdempotencyKey(normalizedIdempotencyKey);
            task.setPayload(payloadJson); task.setStatus("PENDING");
            task.setDispatchStatus("NEW");
            task.setAttempts(0); task.setAvailableAt(LocalDateTime.now());
            task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now());
            tasks.insert(task); return toResponse(task);
        } catch (DuplicateKeyException ex) {
            AiTask existing = tasks.selectOne(new LambdaQueryWrapper<AiTask>()
                    .eq(AiTask::getOwnerId, ownerId).eq(AiTask::getIdempotencyKey, normalizedIdempotencyKey));
            if (existing == null) throw ex;
            return toResponse(existing);
        }
    }

    public AiTaskResponse get(Long id) {
        AiTask task = tasks.selectById(id);
        if (task == null || !requireOwner().equals(task.getOwnerId())) throw new BusinessException(404, "AI 任务不存在: " + id);
        return toResponse(task);
    }

    private Long requireOwner() {
        if (CurrentUserContext.get() == null) throw new BusinessException(401, "AI 任务需要登录账户");
        return CurrentUserContext.get().id();
    }
    private String validateAndWritePayload(String taskType, Object payload) {
        try {
            JsonNode node = objectMapper.valueToTree(payload);
            if (!node.isObject()) throw new BusinessException(400, "任务参数必须是 JSON 对象");
            String json = objectMapper.writeValueAsString(node);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new BusinessException(400, "任务参数不能超过 4 KiB");
            }
            Set<String> allowedFields = switch (taskType) {
                case "GENERATE_QUESTION" -> Set.of("resumeId", "applicationId");
                case "EVALUATE_ANSWER", "FOLLOW_UP", "REPAIR_TURN" -> Set.of("sessionId", "questionId");
                case "FINISH_INTERVIEW" -> Set.of("sessionId");
                default -> throw new BusinessException(400, "不支持的 AI 任务类型");
            };
            node.fieldNames().forEachRemaining(field -> {
                if (!allowedFields.contains(field)) throw new BusinessException(400, "任务参数包含不支持的字段");
            });
            if (taskType.equals("GENERATE_QUESTION")) {
                requirePositiveId(node, "resumeId");
                if (node.hasNonNull("applicationId")) requirePositiveId(node, "applicationId");
            } else if (taskType.equals("FINISH_INTERVIEW")) {
                requirePositiveId(node, "sessionId");
            } else {
                requirePositiveId(node, "sessionId");
                requirePositiveId(node, "questionId");
            }
            return json;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(400, "任务参数格式无效");
        }
    }

    private void requirePositiveId(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new BusinessException(400, "任务参数 " + name + " 必须是正整数");
        }
    }
    private AiTaskResponse toResponse(AiTask task) {
        return new AiTaskResponse(task.getId(), task.getTaskType(), task.getStatus(), task.getAttempts(),
                task.getResult(), task.getErrorMessage(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
