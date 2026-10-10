package com.offertracker.config;

import com.offertracker.service.AiRealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@Profile("realtime")
@EnableWebSocket
public class AiRealtimeWebSocketConfig implements WebSocketConfigurer {
    private final AiRealtimeWebSocketHandler handler;
    public AiRealtimeWebSocketConfig(AiRealtimeWebSocketHandler handler) { this.handler = handler; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { registry.addHandler(handler, "/api/ai/interviews/events").setAllowedOriginPatterns(); }
}
