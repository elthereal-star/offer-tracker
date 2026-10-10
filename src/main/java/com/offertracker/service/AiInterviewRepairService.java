package com.offertracker.service;

import com.offertracker.dto.AiInterviewSessionResponse;
import org.springframework.stereotype.Service;

/** Repairs an interrupted turn by replaying the idempotent evaluation operation. */
@Service
public class AiInterviewRepairService {
    private final AiInterviewService interviews;
    public AiInterviewRepairService(AiInterviewService interviews) { this.interviews = interviews; }
    public AiInterviewSessionResponse repair(Long sessionId, Long questionId) {
        return interviews.evaluate(sessionId, questionId);
    }
}
