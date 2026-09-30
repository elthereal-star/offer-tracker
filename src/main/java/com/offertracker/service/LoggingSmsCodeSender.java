package com.offertracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!sms-cloud")
public class LoggingSmsCodeSender implements SmsCodeSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsCodeSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("开发环境短信验证码 phone={} code={}", phone, code);
    }
}
