package com.offertracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingSmsCodeSenderTest {
    @Test
    void masksPhoneNumberInDevelopmentLogs() {
        assertEquals("+861****01", LoggingSmsCodeSender.maskPhone("+8613900000001"));
        assertEquals("***", LoggingSmsCodeSender.maskPhone("short"));
        assertEquals("***", LoggingSmsCodeSender.maskPhone(null));
    }
}
