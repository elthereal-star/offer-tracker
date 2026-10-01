package com.offertracker.service;

public interface LoginAttemptLimiter {
    void checkAllowed(String phone);
    void clear(String phone);
}
