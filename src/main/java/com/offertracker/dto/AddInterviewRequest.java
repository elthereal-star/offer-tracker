package com.offertracker.dto;

import com.offertracker.enums.InterviewType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AddInterviewRequest(
        @NotNull(message = "面试类型不能为空") InterviewType type,
        LocalDateTime scheduledAt,
        String feedback
) {
}
//这是一行注解