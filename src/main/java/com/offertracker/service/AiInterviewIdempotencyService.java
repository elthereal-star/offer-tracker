package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.entity.AiInterviewRequestIdempotency;
import com.offertracker.mapper.AiInterviewRequestIdempotencyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class AiInterviewIdempotencyService {
    private final AiInterviewRequestIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    public AiInterviewIdempotencyService(AiInterviewRequestIdempotencyMapper mapper, ObjectMapper objectMapper) { this.mapper = mapper; this.objectMapper = objectMapper; }

    public <T> T execute(Long ownerId, Long sessionId, Long questionId, String operation, String requestKey, Class<T> type, Supplier<T> action) {
        if (requestKey == null || requestKey.isBlank()) return action.get();
        String normalized = requestKey.trim();
        if (normalized.length() > 128) throw new com.offertracker.common.BusinessException(400, "X-Idempotency-Key 长度不能超过 128 个字符");
        AiInterviewRequestIdempotency existing = find(ownerId, sessionId, questionId, operation, normalized);
        if (existing != null) return read(existing.getResponseJson(), type);
        T response = action.get();
        AiInterviewRequestIdempotency record = new AiInterviewRequestIdempotency();
        record.setOwnerId(ownerId); record.setSessionId(sessionId); record.setQuestionId(questionId); record.setOperation(operation); record.setRequestKey(normalized);
        try { record.setResponseJson(objectMapper.writeValueAsString(response)); mapper.insert(record); return response; }
        catch (DuplicateKeyException ex) { AiInterviewRequestIdempotency winner = find(ownerId, sessionId, questionId, operation, normalized); return winner == null ? response : read(winner.getResponseJson(), type); }
        catch (Exception ex) { throw new IllegalStateException("AI 面试请求幂等响应保存失败", ex); }
    }
    private AiInterviewRequestIdempotency find(Long ownerId, Long sessionId, Long questionId, String operation, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<AiInterviewRequestIdempotency>().eq(AiInterviewRequestIdempotency::getOwnerId, ownerId).eq(AiInterviewRequestIdempotency::getSessionId, sessionId).eq(AiInterviewRequestIdempotency::getQuestionId, questionId).eq(AiInterviewRequestIdempotency::getOperation, operation).eq(AiInterviewRequestIdempotency::getRequestKey, key));
    }
    private <T> T read(String json, Class<T> type) { try { return objectMapper.readValue(json, type); } catch (Exception ex) { throw new IllegalStateException("AI 面试幂等响应损坏", ex); } }
}
