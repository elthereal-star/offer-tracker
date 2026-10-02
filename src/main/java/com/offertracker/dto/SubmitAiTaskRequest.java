package com.offertracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitAiTaskRequest(
        @NotBlank @Size(max = 64) String taskType,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull JsonNode payload) {}
