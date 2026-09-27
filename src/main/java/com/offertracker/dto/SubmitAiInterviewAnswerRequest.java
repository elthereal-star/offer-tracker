package com.offertracker.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitAiInterviewAnswerRequest(@NotBlank(message = "回答内容不能为空") String answer) {}
