package com.offertracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_tasks")
public class AiTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long ownerId;
    private String taskType;
    private String payload;
    private String status;
    private String idempotencyKey;
    private Integer attempts;
    private LocalDateTime availableAt;
    private LocalDateTime leaseUntil;
    private String result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getOwnerId() { return ownerId; } public void setOwnerId(Long v) { ownerId = v; }
    public String getTaskType() { return taskType; } public void setTaskType(String v) { taskType = v; }
    public String getPayload() { return payload; } public void setPayload(String v) { payload = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public Integer getAttempts() { return attempts; } public void setAttempts(Integer v) { attempts = v; }
    public LocalDateTime getAvailableAt() { return availableAt; } public void setAvailableAt(LocalDateTime v) { availableAt = v; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; } public void setLeaseUntil(LocalDateTime v) { leaseUntil = v; }
    public String getResult() { return result; } public void setResult(String v) { result = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
