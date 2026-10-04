package com.offertracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

/** Optional text/voice-transcription transport. A speech provider can be attached upstream later. */
@Component
@Profile("realtime")
public class AiRealtimeWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper; private final AiInterviewEventHub events;
    private final ConcurrentHashMap<String, Long> lastSequences = new ConcurrentHashMap<>();
    public AiRealtimeWebSocketHandler(ObjectMapper objectMapper, AiInterviewEventHub events) { this.objectMapper = objectMapper; this.events = events; }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        long sessionId = node.path("sessionId").asLong(0), sequence = node.path("sequence").asLong(0);
        String text = node.path("text").asText("").trim();
        if (sessionId <= 0 || sequence <= 0 || text.isBlank() || text.length() > 12000) { session.close(CloseStatus.BAD_DATA); return; }
        String key = session.getId() + ":" + sessionId; Long previous = lastSequences.get(key);
        if (previous != null && sequence <= previous) return;
        lastSequences.put(key, sequence);
        events.publish(sessionId, "transcript", java.util.Map.of("sourceSequence", sequence, "text", text));
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { lastSequences.keySet().removeIf(key -> key.startsWith(session.getId() + ":")); }
}
