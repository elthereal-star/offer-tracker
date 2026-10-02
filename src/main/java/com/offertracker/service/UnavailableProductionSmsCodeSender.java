package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production & !sms-cloud")
public class UnavailableProductionSmsCodeSender implements SmsCodeSender {
    @Override
    public void send(String phone, String code) {
        throw new BusinessException(503, "生产环境未配置短信服务商，请先接入云短信适配器");
    }
}
