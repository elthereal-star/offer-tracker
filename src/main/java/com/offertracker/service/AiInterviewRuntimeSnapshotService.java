package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewRuntimeSnapshot;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewRuntimeSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Durable runtime checkpoint used to rehydrate a session after hot-state loss. */
@Service
public class AiInterviewRuntimeSnapshotService {
    private final AiInterviewRuntimeSnapshotMapper snapshots;
    private final AiInterviewQuestionMapper questions;
    private final ObjectMapper objectMapper;
    private final AiRuntimeArchive archive;
    private final ObjectProvider<AiInterviewEventHub> events;

    public AiInterviewRuntimeSnapshotService(AiInterviewRuntimeSnapshotMapper snapshots,
                                             AiInterviewQuestionMapper questions,
                                             ObjectMapper objectMapper, AiRuntimeArchive archive, ObjectProvider<AiInterviewEventHub> events) {
        this.snapshots = snapshots; this.questions = questions; this.objectMapper = objectMapper; this.archive = archive; this.events = events;
    }

    public void checkpoint(AiInterviewSession session) {
        try {
            List<AiInterviewQuestion> items = questions.selectList(new LambdaQueryWrapper<AiInterviewQuestion>()
                    .eq(AiInterviewQuestion::getSessionId, session.getId())
                    .orderByAsc(AiInterviewQuestion::getQuestionNo));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("status", session.getStatus()); state.put("averageScore", session.getAverageScore());
            state.put("report", session.getReport()); state.put("questions", items);
            AiInterviewRuntimeSnapshot snapshot = snapshots.findBySessionId(session.getId());
            if (snapshot == null) {
                snapshot = new AiInterviewRuntimeSnapshot(); snapshot.setSessionId(session.getId()); snapshot.setVersion(1L);
                snapshot.setCreatedAt(LocalDateTime.now());
            } else snapshot.setVersion(snapshot.getVersion() + 1);
            snapshot.setStateJson(objectMapper.writeValueAsString(state)); snapshot.setUpdatedAt(LocalDateTime.now());
            if (snapshot.getId() == null) snapshots.insert(snapshot); else snapshots.updateById(snapshot);
            archive.append(session.getId(), snapshot.getStateJson(), snapshot.getVersion());
            AiInterviewEventHub hub = events.getIfAvailable();
            if (hub != null) hub.publish(session.getId(), "snapshot", snapshot.getStateJson());
        } catch (Exception ex) {
            throw new IllegalStateException("AI 面试运行态快照保存失败", ex);
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
