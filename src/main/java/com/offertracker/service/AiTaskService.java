package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.AiTaskResponse;
import com.offertracker.entity.AiTask;
import com.offertracker.mapper.AiTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AiTaskService {
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
        try {
            AiTask task = new AiTask();
            task.setOwnerId(ownerId); task.setTaskType(taskType.trim());
            task.setIdempotencyKey(idempotencyKey.trim());
            task.setPayload(writePayload(payload)); task.setStatus("PENDING");
            task.setDispatchStatus("NEW");
            task.setAttempts(0); task.setAvailableAt(LocalDateTime.now());
            task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now());
            tasks.insert(task); return toResponse(task);
        } catch (DuplicateKeyException ex) {
            AiTask existing = tasks.selectOne(new LambdaQueryWrapper<AiTask>()
                    .eq(AiTask::getOwnerId, ownerId).eq(AiTask::getIdempotencyKey, idempotencyKey.trim()));
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
    private String writePayload(Object payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (Exception ex) { throw new BusinessException(400, "任务参数格式无效"); }
    }
    private AiTaskResponse toResponse(AiTask task) {
        return new AiTaskResponse(task.getId(), task.getTaskType(), task.getStatus(), task.getAttempts(),
                task.getResult(), task.getErrorMessage(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
