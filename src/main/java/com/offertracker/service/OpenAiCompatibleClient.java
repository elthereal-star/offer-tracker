package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.AiChatMessage;
import com.offertracker.dto.AiChatRequest;
import com.offertracker.dto.AiChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class OpenAiCompatibleClient {

    private final ObjectMapper objectMapper;
    private final AiConfigService configService;
    private volatile HttpClient httpClient;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public OpenAiCompatibleClient(ObjectMapper objectMapper, AiConfigService configService,
                                 @Value("${offer-tracker.ai.connect-timeout:10s}") Duration connectTimeout,
                                 @Value("${offer-tracker.ai.request-timeout:90s}") Duration requestTimeout) {
        if (connectTimeout.isNegative() || connectTimeout.isZero() || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("AI timeouts must be positive");
        }
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    public String chat(List<AiChatMessage> messages) {
        AiConfigService.StoredConfig config = configService.requireStored();
        try {
            String endpoint = config.baseUrl().endsWith("/chat/completions")
                    ? config.baseUrl() : config.baseUrl() + "/chat/completions";
            String payload = objectMapper.writeValueAsString(new AiChatRequest(config.model(), messages, 0.3));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(502, "AI 服务请求失败（HTTP " + response.statusCode() + "）");
            }
            AiChatResponse result = objectMapper.readValue(response.body(), AiChatResponse.class);
            if (result.choices() == null || result.choices().isEmpty()
                    || result.choices().getFirst().message() == null) {
                throw new BusinessException(502, "AI 服务返回了空回答");
            }
            return result.choices().getFirst().message().content();
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessException(502, "AI 服务连接失败，请检查地址和网络"); }
    }

    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current != null) return current;
        synchronized (this) {
            if (httpClient == null) httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
            return httpClient;
        }
    }
}
