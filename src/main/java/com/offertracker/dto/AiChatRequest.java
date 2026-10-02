package com.offertracker.dto;

import java.util.List;

public record AiChatRequest(String model, List<AiChatMessage> messages, Double temperature) {
}
