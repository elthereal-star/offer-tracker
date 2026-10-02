package com.offertracker.service;

import com.offertracker.entity.AiTask;

public interface AiTaskQueue {
    void publish(AiTask task);
}
