package com.offertracker.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginRequest(@NotBlank String phone, @NotBlank @Size(min = 8, max = 128) String password, @Size(max = 128) String deviceLabel) {}
