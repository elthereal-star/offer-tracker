package com.offertracker.dto;
import java.util.List;
public record AiInterviewSessionResponse(Long id, Long resumeId, Long applicationId, String status, List<AiInterviewQuestionResponse> questions){}
