package com.offertracker.service;

import com.offertracker.dto.AiChatMessage;
import java.util.List;

/** Provider-neutral AI call boundary. Implementations own protocol and retry details. */
public interface AiProviderHandler {
    String chat(AiConfigService.StoredConfig config, List<AiChatMessage> messages);
}
