package com.offertracker.controller;

import com.offertracker.service.AiInterviewEventHub;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("realtime")
@RequestMapping("/api/ai/interviews")
public class AiInterviewRealtimeController {
    private final AiInterviewEventHub events;
    public AiInterviewRealtimeController(AiInterviewEventHub events) { this.events = events; }
    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long sessionId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        long cursor = 0; try { if (lastEventId != null) cursor = Long.parseLong(lastEventId); } catch (NumberFormatException ignored) { }
        return events.subscribe(sessionId, cursor);
    }
}
