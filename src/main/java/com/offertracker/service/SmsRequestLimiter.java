package com.offertracker.service;

public interface SmsRequestLimiter {
    void checkAllowed(String phone);
}
