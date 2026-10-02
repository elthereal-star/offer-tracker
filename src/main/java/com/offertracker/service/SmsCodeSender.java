package com.offertracker.service;

/**
 * Sends a one-time verification code.
 *
 * Production adapters must keep provider credentials outside source control, use bounded
 * connect/read timeouts, avoid logging the code or full phone number, and translate provider
 * failures into {@link com.offertracker.common.BusinessException} without retrying blindly.
 */
public interface SmsCodeSender {
    void send(String phone, String code);
}
