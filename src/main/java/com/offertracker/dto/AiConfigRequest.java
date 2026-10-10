package com.offertracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AiConfigRequest(
        @NotBlank(message = "AI 服务地址不能为空")
        @Pattern(regexp = "https?://.+", message = "AI 服务地址必须以 http:// 或 https:// 开头")
        String baseUrl,
        @NotBlank(message = "模型名称不能为空") String model,
        String apiKey) {
}
