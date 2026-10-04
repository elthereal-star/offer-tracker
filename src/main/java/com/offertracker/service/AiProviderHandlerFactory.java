package com.offertracker.service;

import org.springframework.stereotype.Component;

/** Selects a handler without leaking provider protocol details into interview services. */
@Component
public class AiProviderHandlerFactory {
    private final AiProviderHandler openAiCompatible;
    public AiProviderHandlerFactory(AiProviderHandler openAiCompatible) { this.openAiCompatible = openAiCompatible; }
    public AiProviderHandler forConfig(AiConfigService.StoredConfig config) { return openAiCompatible; }
}
