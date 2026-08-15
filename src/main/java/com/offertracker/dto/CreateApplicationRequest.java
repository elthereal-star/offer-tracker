package com.offertracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.offertracker.enums.ApplicationStatus;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record CreateApplicationRequest(
        @NotNull(message = "公司 ID 不能为空") Long companyId,
        @Size(max = 128, message = "岗位名称不能超过 128 个字符") String position,
        @Size(max = 64, message = "城市不能超过 64 个字符") String city,
        @Size(max = 64, message = "薪资范围不能超过 64 个字符") String salaryRange,
        @Size(max = 64, message = "投递渠道不能超过 64 个字符") String source,
        @URL(message = "岗位链接格式不正确")
        @Size(max = 512, message = "岗位链接不能超过 512 个字符") String jobUrl,
        LocalDate appliedAt,
        @Size(max = 1024, message = "备注不能超过 1024 个字符") String notes,
        ApplicationStatus status
) {
}
