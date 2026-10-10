package com.offertracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "offer-tracker.security")
public record TrustedProxyProperties(List<String> trustedProxyCidrs) {
    public TrustedProxyProperties {
        trustedProxyCidrs = trustedProxyCidrs == null ? List.of() : List.copyOf(trustedProxyCidrs);
    }
}
