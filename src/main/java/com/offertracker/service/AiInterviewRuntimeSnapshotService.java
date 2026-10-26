package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewRuntimeSnapshot;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewRuntimeSnapshotMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable runtime checkpoint used to rehydrate a session after hot-state loss.
 *
 * <p>Layering: the MySQL snapshot row is authoritative, MongoDB is a cold archive consulted only
 * when the authoritative row is missing, and Redis holds a hot copy guarded by a version CAS.
 * Side effects (archive append, event publish, hot copy) run after commit so the write transaction
 * stays in the millisecond range.</p>
 */
@Service
public class AiInterviewRuntimeSnapshotService {
    private final AiInterviewRuntimeSnapshotMapper snapshots;
    private final AiInterviewQuestionMapper questions;
    private final ObjectMapper objectMapper;
    private final AiRuntimeArchive archive;
    private final ObjectProvider<AiInterviewEventHub> events;
    private final ObjectProvider<RedisVersionedState> hotState;

    public AiInterviewRuntimeSnapshotService(AiInterviewRuntimeSnapshotMapper snapshots,
                                             AiInterviewQuestionMapper questions,
                                             ObjectMapper objectMapper, AiRuntimeArchive archive,
                                             ObjectProvider<AiInterviewEventHub> events,
                                             ObjectProvider<RedisVersionedState> hotState) {
        this.snapshots = snapshots; this.questions = questions; this.objectMapper = objectMapper;
        this.archive = archive; this.events = events; this.hotState = hotState;
    }

    public void checkpoint(AiInterviewSession session) {
        String stateJson;
        long version;
        try {
            stateJson = buildStateJson(session);
            version = persist(session.getId(), stateJson);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 面试运行态快照保存失败", ex);
        }
        publishAfterCommit(session.getId(), stateJson, version);
    }

    private String buildStateJson(AiInterviewSession session) throws Exception {
        List<AiInterviewQuestion> items = questions.selectList(new LambdaQueryWrapper<AiInterviewQuestion>()
                .eq(AiInterviewQuestion::getSessionId, session.getId())
                .orderByAsc(AiInterviewQuestion::getQuestionNo));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", session.getStatus());
        state.put("averageScore", session.getAverageScore());
        state.put("report", session.getReport());
        state.put("questions", items);
        return objectMapper.writeValueAsString(state);
    }

    /** Version is incremented by the database, so concurrent turns cannot rewind the snapshot. */
    private long persist(Long sessionId, String stateJson) {
        AiInterviewRuntimeSnapshot existing = snapshots.findBySessionId(sessionId);
        if (existing == null) {
            AiInterviewRuntimeSnapshot created = new AiInterviewRuntimeSnapshot();
            created.setSessionId(sessionId);
            created.setVersion(1L);
            created.setStateJson(stateJson);
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            try {
                snapshots.insert(created);
                return 1L;
            } catch (DuplicateKeyException concurrentCreate) {
                // Another turn created the row first; fall through to the atomic update.
            }
        }
        snapshots.updateState(sessionId, stateJson, LocalDateTime.now());
        Long version = snapshots.selectVersion(sessionId);
        return version == null ? 1L : version;
    }

    private void publishAfterCommit(Long sessionId, String stateJson, long version) {
        Runnable sideEffects = () -> {
            archive.append(sessionId, stateJson, version);
            AiInterviewEventHub hub = events.getIfAvailable();
            if (hub != null) hub.publish(sessionId, "snapshot", stateJson);
            RedisVersionedState hot = hotState.getIfAvailable();
            if (hot != null) hot.compareAndSet("offer:ai:hot:" + sessionId, version - 1, stateJson, Duration.ofMinutes(30));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { sideEffects.run(); }
            });
        } else {
            sideEffects.run();
        }
    }

    public AiInterviewRuntimeSnapshot get(Long sessionId) { return snapshots.findBySessionId(sessionId); }

    /** MySQL is authoritative; Mongo is consulted only when the durable snapshot is absent. */
    public String loadStateJson(Long sessionId) {
        AiInterviewRuntimeSnapshot snapshot = get(sessionId);
        return snapshot == null ? archive.latest(sessionId) : snapshot.getStateJson();
    }

    /** Rehydrates only missing runtime fields; canonical session/question rows remain the source of truth. */
    @org.springframework.transaction.annotation.Transactional
    public boolean rehydrate(AiInterviewSession session) {
        String json = loadStateJson(session.getId());
        if (json == null || json.isBlank()) return false;
        try {
            ObjectNode state = (ObjectNode) objectMapper.readTree(json);
            if (session.getStatus() == null && state.hasNonNull("status")) session.setStatus(state.get("status").asText());
            if (session.getAverageScore() == null && state.hasNonNull("averageScore")) session.setAverageScore(state.get("averageScore").asInt());
            if (session.getReport() == null && state.hasNonNull("report")) session.setReport(state.get("report").asText());
            if (state.has("questions")) {
                List<AiInterviewQuestion> current = questions.selectList(new LambdaQueryWrapper<AiInterviewQuestion>().eq(AiInterviewQuestion::getSessionId, session.getId()));
                java.util.Set<Integer> numbers = current.stream().map(AiInterviewQuestion::getQuestionNo).collect(java.util.stream.Collectors.toSet());
                for (JsonNode node : state.withArray("questions")) {
                    int number = node.path("questionNo").asInt();
                    if (number <= 0 || numbers.contains(number)) continue;
                    AiInterviewQuestion question = objectMapper.treeToValue(node, AiInterviewQuestion.class);
                    question.setId(null); question.setSessionId(session.getId()); questions.insert(question);
                }
            }
            return true;
        } catch (Exception ex) { throw new IllegalStateException("AI 面试运行态恢复失败", ex); }
    }
}
