package com.offertracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateCompanyRequest(
        @NotBlank(message = "公司名称不能为空")
        @Size(max = 128, message = "公司名称不能超过 128 个字符") String name,
        @URL(message = "公司官网格式不正确")
        @Size(max = 256, message = "公司官网不能超过 256 个字符") String website,
        @Size(max = 1024, message = "公司备注不能超过 1024 个字符") String notes
) {
}
