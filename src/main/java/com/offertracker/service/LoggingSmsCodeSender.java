package com.offertracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production & !sms-cloud")
public class LoggingSmsCodeSender implements SmsCodeSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsCodeSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("开发环境短信验证码 phone={} code={}", maskPhone(phone), code);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int prefixLength = phone.startsWith("+") ? 4 : 3;
        return phone.substring(0, prefixLength) + "****" + phone.substring(phone.length() - 2);
    }
}
