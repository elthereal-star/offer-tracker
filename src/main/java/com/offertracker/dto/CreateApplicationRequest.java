package com.offertracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateApplicationRequest(
        @NotNull(message = "公司 ID 不能为空") Long companyId,
        @NotBlank(message = "岗位名称不能为空") String position,
        String city,
        String salaryRange,
        String source,
        String jobUrl,
        LocalDate appliedAt,
        String notes
) {
}
