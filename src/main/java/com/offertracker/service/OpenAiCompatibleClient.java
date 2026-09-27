package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.AiChatMessage;
import com.offertracker.dto.AiChatRequest;
import com.offertracker.dto.AiChatResponse;
import org.springframework.stereotype.Service;

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

    public OpenAiCompatibleClient(ObjectMapper objectMapper, AiConfigService configService) {
        this.objectMapper = objectMapper;
        this.configService = configService;
    }

    public String chat(List<AiChatMessage> messages) {
        AiConfigService.StoredConfig config = configService.requireStored();
        try {
            String endpoint = config.baseUrl().endsWith("/chat/completions")
                    ? config.baseUrl() : config.baseUrl() + "/chat/completions";
            String payload = objectMapper.writeValueAsString(new AiChatRequest(config.model(), messages, 0.3));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
}
