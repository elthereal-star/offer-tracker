package com.offertracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitAiTaskRequest(@NotBlank String taskType, @NotBlank String idempotencyKey, @NotNull JsonNode payload) {}
