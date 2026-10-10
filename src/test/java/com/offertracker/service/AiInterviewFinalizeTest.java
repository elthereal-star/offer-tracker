package com.offertracker.service;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.AiInterviewSessionResponse;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 收尾流程（finish）的结构性约束与 fencing 拒绝语义。
 *
 * <p>这些断言保护的都不是「某个业务分支能不能跑通」，而是几条很容易被后来人无意改掉的
 * 架构决定：AI 调用不能在事务里、题目编号在会话内必须唯一、被 fencing 拒绝时要按幂等收口。</p>
 */
@SpringBootTest
@Transactional
class AiInterviewFinalizeTest {

    @Autowired AiInterviewService service;
    @Autowired AiInterviewSessionMapper sessions;
    @Autowired AiInterviewQuestionMapper questions;
    @MockBean OpenAiCompatibleClient ai;

    @Test
    void fenceRejectionReturnsIdempotentlyWhenTheSessionWasAlreadyFinalized() {
        AiInterviewSession session = newSession();
        questions.insert(question(session.getId(), 1));
        // 更晚的持有者已经落地：令牌被推到 100，状态变成 COMPLETED
        assertEquals(1, sessions.completeWithFence(session.getId(), "COMPLETED", 100, "赢家的报告", LocalDateTime.now(), 100L));

        // 拿一个明显过期的令牌来收口，应当被条件写入拒绝
        AiInterviewSessionResponse response = service.resolveFenceRejection(session.getId(), 5L);

        assertEquals("COMPLETED", response.status());
        assertEquals("赢家的报告", response.report(), "幂等返回的必须是已落地的那份结果，而不是本次的失败");
    }

    @Test
    void fenceRejectionStillReportsConflictWhenTheSessionIsOpen() {
        AiInterviewSession session = newSession();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.resolveFenceRejection(session.getId(), 5L));

        assertEquals(409, error.getCode());
    }

    @Test
    void finishDoesNotRunInsideASingleTransaction() throws Exception {
        Method finish = AiInterviewService.class.getMethod("finish", Long.class);

        assertNull(finish.getAnnotation(Transactional.class),
                "finish 不能再带 @Transactional —— 否则 90 秒的 AI 调用会被包进一个长事务，"
                        + "占用数据库连接与行锁。事务边界应交给方法内部的 TransactionTemplate 分两段控制。");
    }

    @Test
    void rejectsDuplicateQuestionNumberWithinTheSameSession() {
        AiInterviewSession session = newSession();
        questions.insert(question(session.getId(), 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> questions.insert(question(session.getId(), 1)),
                "同一会话内的 question_no 必须唯一，否则追问的“下一题”判定会失效");
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

    private AiInterviewQuestion question(Long sessionId, int questionNo) {
        AiInterviewQuestion question = new AiInterviewQuestion();
        question.setSessionId(sessionId);
        question.setQuestionNo(questionNo);
        question.setContent("第 " + questionNo + " 题");
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }
}
