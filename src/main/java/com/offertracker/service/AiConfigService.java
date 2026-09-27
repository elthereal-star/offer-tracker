package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.AiConfigRequest;
import com.offertracker.dto.AiConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class AiConfigService {

    private final ObjectMapper objectMapper;
    private final Path configPath;

    public AiConfigService(ObjectMapper objectMapper,
                           @Value("${offer-tracker.storage.ai-config-file:./data/ai-config.json}") String configPath) {
        this.objectMapper = objectMapper;
        this.configPath = Path.of(configPath).toAbsolutePath().normalize();
    }

    public synchronized AiConfigResponse view() {
        StoredConfig config = read();
        if (config == null) return new AiConfigResponse(false, "", "", "");
        return new AiConfigResponse(true, config.baseUrl(), config.model(), mask(config.apiKey()));
    }

    public synchronized AiConfigResponse save(AiConfigRequest request) {
        String apiKey = blankToNull(request.apiKey());
        StoredConfig existing = read();
        if (apiKey == null && existing != null) apiKey = existing.apiKey();
        if (apiKey == null) throw new BusinessException(400, "API Key 不能为空");
        StoredConfig config = new StoredConfig(request.baseUrl().trim().replaceAll("/+$", ""), request.model().trim(), apiKey);
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, objectMapper.writeValueAsString(config), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return view();
        } catch (IOException ex) {
            throw new BusinessException(500, "AI 配置保存失败");
        }
    }

    public synchronized void clear() {
        try { Files.deleteIfExists(configPath); } catch (IOException ex) { throw new BusinessException(500, "AI 配置清除失败"); }
    }

    public synchronized StoredConfig requireStored() {
        StoredConfig config = read();
        if (config == null || blankToNull(config.apiKey()) == null) {
            throw new BusinessException(400, "请先在 AI 设置中配置 API Key");
        }
        return config;
    }

    private StoredConfig read() {
        if (!Files.exists(configPath)) return null;
        try { return objectMapper.readValue(Files.readString(configPath), StoredConfig.class); }
        catch (IOException | RuntimeException ex) { throw new BusinessException(500, "AI 配置文件损坏，请重新配置"); }
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "••••••••";
        return key.substring(0, 4) + "••••" + key.substring(key.length() - 4);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record StoredConfig(String baseUrl, String model, String apiKey) { }
}
