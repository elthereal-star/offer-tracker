package com.offertracker.service;

/** Sends a one-time verification code. Production deployments provide a cloud SMS adapter. */
public interface SmsCodeSender {
    void send(String phone, String code);
}
