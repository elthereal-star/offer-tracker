package com.offertracker.dto;

import java.util.List;

public record AiChatResponse(List<Choice> choices) {
    public record Choice(Message message) { }
    public record Message(String role, String content) { }
}
