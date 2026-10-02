package com.offertracker.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record SendVerificationCodeRequest(@NotBlank @Pattern(regexp = "^\\+?[0-9]{6,18}$", message = "手机号格式不正确") String phone, @NotBlank @Pattern(regexp = "^REGISTER$", message = "验证码用途不正确") String purpose) {}
