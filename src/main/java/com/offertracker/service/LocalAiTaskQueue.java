package com.offertracker.service;

import com.offertracker.entity.AiTask;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Profile("!production")
public class LocalAiTaskQueue implements AiTaskQueue {
    public LocalAiTaskQueue(MeterRegistry metrics) { }

    @Override
    public void publish(AiTask task) {
        // Local and test profiles persist task state without requiring Redis.
    }
}
