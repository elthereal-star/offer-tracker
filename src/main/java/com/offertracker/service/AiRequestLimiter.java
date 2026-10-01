package com.offertracker.service;

public interface AiRequestLimiter {
    void checkAllowed(Long userId);
}
