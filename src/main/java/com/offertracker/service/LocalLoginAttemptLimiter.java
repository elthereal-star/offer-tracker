package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class LocalLoginAttemptLimiter implements LoginAttemptLimiter {
    @Override public void checkAllowed(String phone) { }
    @Override public void clear(String phone) { }
}
