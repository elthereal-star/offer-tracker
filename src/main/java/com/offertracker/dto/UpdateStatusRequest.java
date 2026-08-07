package com.offertracker.dto;

import com.offertracker.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "状态不能为空") ApplicationStatus status
) {
}
