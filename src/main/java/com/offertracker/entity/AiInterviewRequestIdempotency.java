package com.offertracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_interview_request_idempotency")
public class AiInterviewRequestIdempotency {
    @TableId(type = IdType.AUTO) private Long id;
    private Long ownerId; private Long sessionId; private Long questionId;
    private String operation; private String requestKey; private String responseJson; private LocalDateTime createdAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getOwnerId() { return ownerId; } public void setOwnerId(Long v) { ownerId = v; }
    public Long getSessionId() { return sessionId; } public void setSessionId(Long v) { sessionId = v; }
    public Long getQuestionId() { return questionId; } public void setQuestionId(Long v) { questionId = v; }
    public String getOperation() { return operation; } public void setOperation(String v) { operation = v; }
    public String getRequestKey() { return requestKey; } public void setRequestKey(String v) { requestKey = v; }
    public String getResponseJson() { return responseJson; } public void setResponseJson(String v) { responseJson = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
