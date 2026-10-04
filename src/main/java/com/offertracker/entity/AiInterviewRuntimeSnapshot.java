package com.offertracker.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_interview_runtime_snapshots")
public class AiInterviewRuntimeSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId;
    private Long version;
    private String stateJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getSessionId() { return sessionId; } public void setSessionId(Long v) { sessionId = v; }
    public Long getVersion() { return version; } public void setVersion(Long v) { version = v; }
    public String getStateJson() { return stateJson; } public void setStateJson(String v) { stateJson = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
