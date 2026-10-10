package com.offertracker;

import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewSessionMapper;
import com.offertracker.service.OpenAiCompatibleClient;
import com.offertracker.service.rule.AiFollowUpContext;
import com.offertracker.service.rule.AiFollowUpOutcome;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.FlowBus;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追问裁决链（LiteFlow）验证。
 *
 * <p>{@code AiInterviewService.followUp()} 已不再内联 if 判断，全部裁决交给
 * {@code aiInterviewFollowUpChain}。本测试直接驱动这条链，逐条断言守卫顺序与
 * 裁决结果，确保规则文件被加载、节点被扫描到，并且「判定」与「HTTP 语义翻译」
 * 两件事仍然对齐。</p>
 */
@SpringBootTest
@Transactional
class AiFollowUpChainTest {

    private static final String CHAIN_ID = "aiInterviewFollowUpChain";

    @Autowired FlowExecutor flowExecutor;
    @Autowired AiInterviewSessionMapper sessions;
    @Autowired AiInterviewQuestionMapper questions;
    @MockBean OpenAiCompatibleClient ai;

    @Test
    void chainAndItsNodesAreRegistered() {
        assertTrue(FlowBus.containChain(CHAIN_ID), "规则文件 classpath:liteflow/ai-interview-followup.xml 必须被加载");
        assertNotNull(FlowBus.getChain(CHAIN_ID));

        for (String nodeId : new String[]{
                "requireActiveGuard",
                "loadFollowUpContext",
                "requireLatestQuestionGuard",
                "requireEvaluationGuard",
                "followUpDecisionFinalize"}) {
            assertNotNull(FlowBus.getNode(nodeId), "节点未注册: " + nodeId);
        }
    }

    @Test
    void rejectsWhenQuestionDoesNotExist() {
        AiInterviewSession session = newSession("ACTIVE");

        AiFollowUpContext context = decide(session, 9_999_999L);

        assertEquals(AiFollowUpOutcome.REJECTED, context.getOutcome());
        assertEquals(404, context.getRejectionCode());
    }

    @Test
    void rejectsWhenSessionAlreadyTerminated() {
        AiInterviewSession session = newSession("COMPLETED");
        AiInterviewQuestion question = insertQuestion(session.getId(), 1);

        AiFollowUpContext context = decide(session, question.getId());

        assertEquals(AiFollowUpOutcome.REJECTED, context.getOutcome());
        assertEquals(409, context.getRejectionCode());
        assertEquals("AI 面试会话已结束", context.getRejectionMessage());
    }

    @Test
    void rejectsBeforeEvaluation() {
        AiInterviewSession session = newSession("ACTIVE");
        AiInterviewQuestion question = insertQuestion(session.getId(), 1);

        AiFollowUpContext context = decide(session, question.getId());

        assertEquals(AiFollowUpOutcome.REJECTED, context.getOutcome());
        assertEquals(409, context.getRejectionCode());
        assertEquals("请先完成当前题目的 AI 评分", context.getRejectionMessage());
    }

    @Test
    void skipsStaleQuestionRequest() {
        AiInterviewSession session = newSession("ACTIVE");
        AiInterviewQuestion first = insertQuestion(session.getId(), 1);

        // 先给第一题补上评分，确保拦截原因是「非最新题」而不是「未评分」
        first.setScore(80);
        first.setFeedback("回答到位");
        questions.updateById(first);

        // 会话里已经存在第 2 题，此时对第 1 题再请求追问属于前端重试，应幂等短路
        insertQuestion(session.getId(), 2);

        AiFollowUpContext context = decide(session, first.getId());

        assertEquals(AiFollowUpOutcome.SKIPPED, context.getOutcome(), "旧题重复追问必须短路，而不是报错");
    }

    @Test
    void computesNextQuestionNoAndPromptWhenAllGuardsPass() {
        AiInterviewSession session = newSession("ACTIVE");
        AiInterviewQuestion first = insertQuestion(session.getId(), 1);
        first.setAnswer("我做过一个投递追踪系统");
        first.setScore(88);
        first.setFeedback("结构清晰，但缺少量化结果");
        questions.updateById(first);

        AiFollowUpContext context = decide(session, first.getId());

        assertEquals(AiFollowUpOutcome.READY, context.getOutcome());
        assertEquals(2, context.getNextQuestionNo().intValue(), "下一题号应为最新题号 + 1");
        assertNotNull(context.getPrompt());
        assertTrue(context.getPrompt().contains("我做过一个投递追踪系统"), "提示词应带上上一题的回答");
        assertTrue(context.getPrompt().contains("结构清晰，但缺少量化结果"), "提示词应带上评分反馈");
    }

    @Test
    void nextQuestionNoFollowsLatestQuestionNotTheRequestedOne() {
        AiInterviewSession session = newSession("ACTIVE");
        AiInterviewQuestion first = insertQuestion(session.getId(), 1);
        first.setScore(80);
        first.setFeedback("可以更具体");
        questions.updateById(first);

        AiInterviewQuestion second = insertQuestion(session.getId(), 2);
        second.setScore(85);
        second.setFeedback("思路清楚");
        questions.updateById(second);

        AiFollowUpContext context = decide(session, second.getId());

        assertEquals(AiFollowUpOutcome.READY, context.getOutcome());
        assertEquals(3, context.getNextQuestionNo().intValue(), "题号必须跟在最新题之后，避免编号重复");
    }

    private AiFollowUpContext decide(AiInterviewSession session, Long questionId) {
        AiFollowUpContext context = new AiFollowUpContext(session.getId(), questionId, session);
        LiteflowResponse response = flowExecutor.execute2Resp(CHAIN_ID, null, context);
        assertTrue(response.isSuccess(), "链执行失败: " + response.getMessage());
        return context;
    }

    private AiInterviewSession newSession(String status) {
        AiInterviewSession session = new AiInterviewSession();
        session.setResumeId(1L);
        session.setStatus(status);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessions.insert(session);
        return session;
    }

    private AiInterviewQuestion insertQuestion(Long sessionId, int questionNo) {
        AiInterviewQuestion question = new AiInterviewQuestion();
        question.setSessionId(sessionId);
        question.setQuestionNo(questionNo);
        question.setContent("第 " + questionNo + " 题");
        question.setCreatedAt(LocalDateTime.now());
        questions.insert(question);
        return question;
    }
}
