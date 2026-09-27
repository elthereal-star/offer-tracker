package com.offertracker.dto;
import jakarta.validation.constraints.NotNull;
public record CreateAiInterviewRequest(@NotNull Long resumeId, Long applicationId){}
