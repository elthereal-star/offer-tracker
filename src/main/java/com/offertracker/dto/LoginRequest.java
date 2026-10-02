package com.offertracker.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record LoginRequest(@NotBlank @Pattern(regexp = "^\\+?[0-9]{6,18}$", message = "手机号格式不正确") String phone, @NotBlank @Size(min = 8, max = 128) String password) {}
