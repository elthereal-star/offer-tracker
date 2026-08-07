package com.offertracker.dto;

import com.offertracker.enums.InterviewResult;
import jakarta.validation.constraints.NotNull;

public record UpdateInterviewResultRequest(
        @NotNull(message = "面试结果不能为空") InterviewResult result,
        String feedback
) {
}
