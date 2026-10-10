package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional SSE transport. Events are advisory; MySQL snapshots remain authoritative. */
@Service
@Profile("realtime")
public class AiInterviewEventHub {
    private final ConcurrentHashMap<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final AiMessageSequenceService sequences;
    public AiInterviewEventHub(AiMessageSequenceService sequences) { this.sequences = sequences; }
    public SseEmitter subscribe(Long sessionId, long lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter)); emitter.onTimeout(() -> remove(sessionId, emitter)); emitter.onError(ex -> remove(sessionId, emitter));
        try { emitter.send(SseEmitter.event().name("ready").id(String.valueOf(Math.max(lastEventId, 0))).data("connected")); }
        catch (IOException ex) { remove(sessionId, emitter); }
        return emitter;
    }
    public void publish(Long sessionId, String type, Object payload) {
        long sequence = sequences.next(sessionId);
        for (SseEmitter emitter : subscribers.getOrDefault(sessionId, Set.of())) {
            try { emitter.send(SseEmitter.event().name(type).id(String.valueOf(sequence)).data(payload)); }
            catch (IOException ex) { remove(sessionId, emitter); }
        }
    }
    private void remove(Long sessionId, SseEmitter emitter) { Set<SseEmitter> set = subscribers.get(sessionId); if (set != null) { set.remove(emitter); if (set.isEmpty()) subscribers.remove(sessionId, set); } }
}
