package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.AiInterviewSessionResponse;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewSessionMapper;
import com.offertracker.service.AiInterviewService;
import com.offertracker.service.AiSessionFence;
import com.offertracker.service.AiSessionLock;
import com.offertracker.service.OpenAiCompatibleClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Fencing token 行为验证：发号单调、发号时机正确、存储层拒绝被取代的持有者。
 */
@SpringBootTest
@Transactional
class AiSessionFenceTest {

    private static final AtomicLong SESSION_ID_SEQ = new AtomicLong(9_100_000L);

    @Autowired AiSessionFence fence;
    @Autowired AiSessionLock sessionLock;
    @Autowired AiInterviewSessionMapper sessions;
    @Autowired AiInterviewQuestionMapper questions;
    @Autowired AiInterviewService service;
    @MockBean OpenAiCompatibleClient ai;

    @Test
    void issuesStrictlyIncreasingTokensPerSession() {
        long sessionId = SESSION_ID_SEQ.incrementAndGet();
        long first = fence.issue(sessionId);
        long second = fence.issue(sessionId);
        long third = fence.issue(sessionId);

        assertTrue(second > first, "同一会话的令牌必须严格递增");
        assertTrue(third > second, "同一会话的令牌必须严格递增");
        assertEquals(0L, fence.issue(null), "sessionId 为空时返回 0，表示不参与防护");
    }

    @Test
    void issuesTokenAboveTheValueAlreadyStoredInDatabase() {
        AiInterviewSession session = newSession();
        // 模拟“上一个进程写过令牌、本进程计数器还是空的”这一场景。
        assertEquals(1, sessions.completeWithFence(session.getId(), "COMPLETED", 80, "上一进程写的报告", LocalDateTime.now(), 5L));

        long token = fence.issue(session.getId());

        assertEquals(6L, token, "发号必须大于库中已记录值，否则条件写入恒不成立、会话永久无法收尾");
    }

    @Test
    void tokensAreScopedPerSession() {
        long first = SESSION_ID_SEQ.incrementAndGet();
        long second = SESSION_ID_SEQ.incrementAndGet();

        assertEquals(1L, fence.issue(first));
        assertEquals(1L, fence.issue(second), "不同会话各自独立发号");
    }

    @Test
    void lockHandsOutTokenAfterAcquiring() {
        long sessionId = SESSION_ID_SEQ.incrementAndGet();

        long insideFirst = sessionLock.executeFenced(sessionId, token -> token);
        long insideSecond = sessionLock.executeFenced(sessionId, token -> token);

        assertTrue(insideFirst > 0L);
        assertTrue(insideSecond > insideFirst, "后一次持锁拿到的令牌必须更大");
    }

    @Test
    void stillSupportsUnfencedExecution() {
        long sessionId = SESSION_ID_SEQ.incrementAndGet();
        assertEquals("done", sessionLock.execute(sessionId, () -> "done"));
    }

    @Test
    void rejectsWriteFromSupersededHolder() {
        AiInterviewSession session = newSession();

        assertEquals(1, sessions.completeWithFence(session.getId(), "COMPLETED", 80, "第一次报告", LocalDateTime.now(), 5L));
        assertEquals(5L, sessions.selectFenceToken(session.getId()).longValue());

        // 租约过期后仍在跑的旧持有者拿旧令牌回写：必须被存储层拒绝
        assertEquals(0, sessions.completeWithFence(session.getId(), "COMPLETED", 10, "过期报告", LocalDateTime.now(), 3L));
        // 令牌要求严格大于，重复使用同一个令牌同样不允许
        assertEquals(0, sessions.completeWithFence(session.getId(), "COMPLETED", 10, "过期报告", LocalDateTime.now(), 5L));

        AiInterviewSession reloaded = sessions.selectById(session.getId());
        assertEquals("第一次报告", reloaded.getReport(), "过期写入不得覆盖已有结果");
        assertEquals(80, reloaded.getAverageScore().intValue());
        assertEquals(5L, sessions.selectFenceToken(session.getId()).longValue(), "被拒绝的写入不得推进令牌");

        // 更晚的持有者可以正常推进
        assertEquals(1, sessions.completeWithFence(session.getId(), "COMPLETED", 90, "新报告", LocalDateTime.now(), 6L));
        assertEquals("新报告", sessions.selectById(session.getId()).getReport());
        assertEquals(6L, sessions.selectFenceToken(session.getId()).longValue());
    }

    @Test
    void finishRecordsFenceTokenOnCompletion() {
        when(ai.chat(any())).thenReturn("整体表现稳定，建议加强系统设计表达。");
        AiInterviewSession session = newSession();
        questions.insert(scoredQuestion(session.getId()));

        AiInterviewSessionResponse response = service.finish(session.getId());

        assertEquals("COMPLETED", response.status());
        Long token = sessions.selectFenceToken(session.getId());
        assertNotNull(token);
        assertTrue(token > 0L, "收尾写入必须带上 fencing 令牌");
        assertEquals(88, response.averageScore().intValue());
    }

    @Test
    void repeatedFinishStaysIdempotent() {
        when(ai.chat(any())).thenReturn("第一版报告");
        AiInterviewSession session = newSession();
        questions.insert(scoredQuestion(session.getId()));

        AiInterviewSessionResponse first = service.finish(session.getId());
        Long tokenAfterFirst = sessions.selectFenceToken(session.getId());
        AiInterviewSessionResponse second = service.finish(session.getId());

        assertEquals("COMPLETED", second.status());
        assertEquals(first.report(), second.report(), "重复收尾应直接返回既有结果");
        assertEquals(tokenAfterFirst, sessions.selectFenceToken(session.getId()), "幂等路径不应再次推进令牌");
    }

    @Test
    void rejectsCompletionWithoutAnyScoredQuestion() {
        AiInterviewSession session = newSession();
        assertThrows(BusinessException.class, () -> service.finish(session.getId()));
    }

    private AiInterviewSession newSession() {
        AiInterviewSession session = new AiInterviewSession();
        session.setResumeId(1L);
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessions.insert(session);
        return session;
    }

    private AiInterviewQuestion scoredQuestion(Long sessionId) {
        AiInterviewQuestion question = new AiInterviewQuestion();
        question.setSessionId(sessionId);
        question.setQuestionNo(1);
        question.setContent("介绍一下你最有挑战的项目");
        question.setAnswer("我做过一个投递追踪系统");
        question.setScore(88);
        question.setFeedback("结构清晰，但缺少量化结果");
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }
}
