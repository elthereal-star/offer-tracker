package com.offertracker.service;

import com.offertracker.common.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class AiProviderEndpointPolicy {
    private final boolean production;

    public AiProviderEndpointPolicy(Environment environment) {
        this.production = environment.acceptsProfiles(Profiles.of("production"));
    }

    public String normalize(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw invalidEndpoint();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!uri.isAbsolute() || scheme == null || host == null || host.isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalidEndpoint();
        }
        if (production && (!scheme.equalsIgnoreCase("https") || isLocalHost(host) || isIpLiteral(host))) {
            throw invalidProductionEndpoint();
        }
        return uri.toASCIIString().replaceAll("/+$", "");
    }

    private boolean isLocalHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.endsWith(".localhost")
                || normalized.endsWith(".local") || normalized.endsWith(".internal")
                || normalized.endsWith(".svc");
    }

    private boolean isIpLiteral(String host) {
        String normalized = host;
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.contains(":") || normalized.matches("[0-9.]+");
    }

    private BusinessException invalidEndpoint() {
        return new BusinessException(400, "AI 服务地址不合法");
    }

    private BusinessException invalidProductionEndpoint() {
        return new BusinessException(400, "生产环境 AI 服务地址必须使用 HTTPS 域名，不支持本机或 IP 地址");
    }
}
