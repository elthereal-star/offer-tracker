package com.offertracker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.offertracker.config.TrustedProxyProperties;

@SpringBootApplication
@MapperScan("com.offertracker.mapper")
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class OfferTrackerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OfferTrackerApplication.class);
        if (DesktopRuntime.isEnabled()) {
            if (!DesktopRuntime.prepare()) {
                return;
            }
            application.setHeadless(false);
            DesktopRuntime.applySystemProperties();
            application.addListeners((ApplicationListener<ApplicationReadyEvent>) DesktopRuntime::onReady);
        }
        application.run(args);
    }
}
