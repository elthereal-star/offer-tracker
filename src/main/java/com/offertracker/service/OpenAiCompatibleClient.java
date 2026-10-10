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
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

@Service
public class OpenAiCompatibleClient implements AiProviderHandler {

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
        return request(config, messages);
    }

    private String request(AiConfigService.StoredConfig config, List<AiChatMessage> messages) {
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
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        AiChatResponse result = objectMapper.readValue(response.body(), AiChatResponse.class);
                        if (result.choices() == null || result.choices().isEmpty() || result.choices().getFirst().message() == null) throw new AiProviderException(502, "AI 服务返回了空回答", false);
                        return result.choices().getFirst().message().content();
                    }
                    boolean retryable = response.statusCode() == 408 || response.statusCode() == 409 || response.statusCode() == 429 || response.statusCode() >= 500;
                    if (!retryable || attempt == 3) throw new AiProviderException(502, "AI 服务请求失败（HTTP " + response.statusCode() + "）", retryable);
                } catch (AiProviderException ex) { throw ex; }
                catch (java.io.IOException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    if (attempt == 3) throw new AiProviderException(502, "AI 服务连接失败，请检查地址和网络", true);
                }
                try { Thread.sleep((1L << attempt) * 100L + ThreadLocalRandom.current().nextLong(100)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AiProviderException(502, "AI 请求重试被中断", true); }
            }
            throw new AiProviderException(502, "AI 服务请求失败，请稍后重试", true);
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

    @Override
    public String chat(AiConfigService.StoredConfig config, List<AiChatMessage> messages) {
        return request(config, messages);
    }
}
