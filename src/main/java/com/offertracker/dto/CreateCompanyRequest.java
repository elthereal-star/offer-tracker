package com.offertracker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank(message = "公司名称不能为空") String name,
        String website,
        String notes
) {
}
