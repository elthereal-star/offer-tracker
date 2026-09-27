package com.offertracker.dto;
import java.util.List;
public record AiInterviewSessionResponse(Long id, Long resumeId, Long applicationId, String status, Integer averageScore, String report, List<AiInterviewQuestionResponse> questions){}
