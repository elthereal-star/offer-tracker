package com.offertracker.service.rule;

import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewSession;

/**
 * 追问裁决链的上下文：既承载输入，也承载裁决结果。
 *
 * <p>节点之间不通过返回值传递状态，而是统一写入本对象。这样任何一个节点的判定
 * 都能被后续节点与调用方看到，也让整条链可以在不启动 Spring 的情况下单测。</p>
 */
public class AiFollowUpContext {

    private final Long sessionId;
    private final Long questionId;
    private final AiInterviewSession session;

    private AiInterviewQuestion previous;
    private int latestQuestionNo;
    private AiFollowUpOutcome outcome = AiFollowUpOutcome.PENDING;
    private int rejectionCode;
    private String rejectionMessage;
    private Integer nextQuestionNo;
    private String prompt;

    public AiFollowUpContext(Long sessionId, Long questionId, AiInterviewSession session) {
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.session = session;
    }

    /** 已有节点给出结论；后续节点应直接跳过，避免覆盖结论。 */
    public boolean decided() {
        return outcome != AiFollowUpOutcome.PENDING;
    }

    public void reject(int code, String message) {
        this.outcome = AiFollowUpOutcome.REJECTED;
        this.rejectionCode = code;
        this.rejectionMessage = message;
    }

    public void skip() {
        this.outcome = AiFollowUpOutcome.SKIPPED;
    }

    public void ready(int nextQuestionNo, String prompt) {
        this.outcome = AiFollowUpOutcome.READY;
        this.nextQuestionNo = nextQuestionNo;
        this.prompt = prompt;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public AiInterviewSession getSession() {
        return session;
    }

    public AiInterviewQuestion getPrevious() {
        return previous;
    }

    public void setPrevious(AiInterviewQuestion previous) {
        this.previous = previous;
    }

    public int getLatestQuestionNo() {
        return latestQuestionNo;
    }

    public void setLatestQuestionNo(int latestQuestionNo) {
        this.latestQuestionNo = latestQuestionNo;
    }

    public AiFollowUpOutcome getOutcome() {
        return outcome;
    }

    public int getRejectionCode() {
        return rejectionCode;
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }

    public Integer getNextQuestionNo() {
        return nextQuestionNo;
    }

    public String getPrompt() {
        return prompt;
    }
}
