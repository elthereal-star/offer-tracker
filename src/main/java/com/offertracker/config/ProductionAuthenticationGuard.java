package com.offertracker.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionAuthenticationGuard implements InitializingBean {
    private final boolean authenticationRequired;

    public ProductionAuthenticationGuard(
            @Value("${offer-tracker.auth.required:true}") boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    @Override
    public void afterPropertiesSet() {
        if (!authenticationRequired) {
            throw new IllegalStateException(
                    "Authentication cannot be disabled while the production profile is active");
        }
    }
}
