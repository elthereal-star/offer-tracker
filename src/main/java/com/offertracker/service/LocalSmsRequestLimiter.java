package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class LocalSmsRequestLimiter implements SmsRequestLimiter {
    @Override
    public void checkAllowed(String phone) {
        // Local development does not require a Redis service.
    }
}
