package com.offertracker.service;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!mongo-archive")
public class NoopAiRuntimeArchive implements AiRuntimeArchive {
    @Override public void append(Long sessionId, String stateJson, long version) { }
}
