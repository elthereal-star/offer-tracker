package com.offertracker.dto;

public record AiConfigResponse(
        boolean configured,
        String baseUrl,
        String model,
        String maskedApiKey) {
}
