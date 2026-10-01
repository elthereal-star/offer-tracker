package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class LocalAiRequestLimiter implements AiRequestLimiter {
    @Override public void checkAllowed(Long userId) { }
}
