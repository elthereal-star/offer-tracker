package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional SSE transport. Events are advisory; MySQL snapshots remain authoritative.
 *
 * <p>Each session keeps a bounded ring buffer of recent frames. A reconnect that carries
 * {@code Last-Event-ID} receives every frame after that cursor before new frames are appended, so a
 * dropped connection does not silently lose turns. Subscription and publishing share one lock per
 * session, which keeps replay and live delivery in strictly increasing sequence order.</p>
 */
@Service
@Profile("realtime")
public class AiInterviewEventHub {
    private static final int HISTORY_LIMIT = 256;
    private final ConcurrentHashMap<Long, SessionChannel> channels = new ConcurrentHashMap<>();
    private final AiMessageSequenceService sequences;

    public AiInterviewEventHub(AiMessageSequenceService sequences) { this.sequences = sequences; }

    public SseEmitter subscribe(Long sessionId, long lastEventId) {
        SessionChannel channel = channel(sessionId);
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));
        synchronized (channel.lock) {
            for (EventFrame frame : channel.history) {
                if (frame.sequence() <= lastEventId) continue;
                try {
                    emitter.send(frame.toSseEvent());
                } catch (IOException ex) {
                    remove(sessionId, emitter);
                    return emitter;
                }
            }
            channel.subscribers.add(emitter);
            try {
                emitter.send(SseEmitter.event().name("ready").id(String.valueOf(Math.max(lastEventId, 0))).data("connected"));
            } catch (IOException ex) {
                remove(sessionId, emitter);
            }
        }
        return emitter;
    }

    public void publish(Long sessionId, String type, Object payload) {
        SessionChannel channel = channel(sessionId);
        synchronized (channel.lock) {
            EventFrame frame = new EventFrame(sequences.next(sessionId), type, payload);
            channel.history.addLast(frame);
            while (channel.history.size() > HISTORY_LIMIT) channel.history.removeFirst();
            for (SseEmitter emitter : channel.subscribers) {
                try {
                    emitter.send(frame.toSseEvent());
                } catch (IOException ex) {
                    channel.subscribers.remove(emitter);
                }
            }
        }
    }

    private SessionChannel channel(Long sessionId) {
        return channels.computeIfAbsent(sessionId, ignored -> new SessionChannel());
    }

    private void remove(Long sessionId, SseEmitter emitter) {
        SessionChannel channel = channels.get(sessionId);
        if (channel == null) return;
        synchronized (channel.lock) {
            channel.subscribers.remove(emitter);
            if (channel.subscribers.isEmpty()) channels.remove(sessionId, channel);
        }
    }

    private record EventFrame(long sequence, String type, Object payload) {
        SseEmitter.SseEventBuilder toSseEvent() {
            return SseEmitter.event().name(type).id(String.valueOf(sequence)).data(payload);
        }
    }

    private static final class SessionChannel {
        private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();
        private final Deque<EventFrame> history = new ArrayDeque<>();
        private final Object lock = new Object();
    }
}
