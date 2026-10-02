package com.offertracker.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record RegisterRequest(@NotBlank @Pattern(regexp = "^\\+?[0-9]{6,18}$", message = "手机号格式不正确") String phone, @NotBlank @Size(min = 8, max = 128, message = "密码长度必须为 8 到 128 位") String password) {}
