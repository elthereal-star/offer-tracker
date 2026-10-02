package com.offertracker.dto;

import java.time.LocalDateTime;

public record AiTaskResponse(Long id, String taskType, String status, Integer attempts,
                             String result, String errorMessage, LocalDateTime createdAt,
                             LocalDateTime updatedAt) {}
