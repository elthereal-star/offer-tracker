package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.AiConfigRequest;
import com.offertracker.dto.AiConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AiConfigService {
    private static final int GCM_TAG_BITS = 128;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final Path configPath;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public AiConfigService(ObjectMapper objectMapper, JdbcTemplate jdbc,
                           @Value("${offer-tracker.storage.ai-config-file:./data/ai-config.json}") String configPath,
                           @Value("${offer-tracker.auth.config-encryption-key}") String encryptionSecret,
                           @Value("${offer-tracker.auth.required:false}") boolean authRequired) {
        this.objectMapper = objectMapper; this.jdbc = jdbc;
        this.configPath = Path.of(configPath).toAbsolutePath().normalize();
        if (encryptionSecret == null || encryptionSecret.length() < 32) throw new IllegalArgumentException("AI_CONFIG_ENCRYPTION_KEY must be at least 32 characters");
        if (authRequired && encryptionSecret.startsWith("local-development-only-")) {
            throw new IllegalArgumentException("AI_CONFIG_ENCRYPTION_KEY must be explicitly configured when authentication is required");
        }
        try { this.encryptionKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(encryptionSecret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception ex) { throw new IllegalStateException("AI 配置加密密钥初始化失败", ex); }
    }

    public synchronized AiConfigResponse view() { StoredConfig c = currentUserId() == null ? readFile() : readUser(currentUserId()); return c == null ? new AiConfigResponse(false, "", "", "") : new AiConfigResponse(true, c.baseUrl(), c.model(), mask(c.apiKey())); }

    public synchronized AiConfigResponse save(AiConfigRequest request) {
        String key = blankToNull(request.apiKey()); StoredConfig old = currentUserId() == null ? readFile() : readUser(currentUserId());
        if (key == null && old != null) key = old.apiKey(); if (key == null) throw new BusinessException(400, "API Key 不能为空");
        StoredConfig c = new StoredConfig(request.baseUrl().trim().replaceAll("/+$", ""), request.model().trim(), key);
        if (currentUserId() == null) writeFile(c); else writeUser(currentUserId(), c); return view();
    }

    public synchronized void clear() {
        if (currentUserId() == null) { try { Files.deleteIfExists(configPath); } catch (IOException ex) { throw new BusinessException(500, "AI 配置清除失败"); } }
        else jdbc.update("DELETE FROM ai_user_configs WHERE user_id = ?", currentUserId());
    }

    public synchronized StoredConfig requireStored() {
        StoredConfig c = currentUserId() == null ? readFile() : readUser(currentUserId());
        if (c == null || blankToNull(c.apiKey()) == null) throw new BusinessException(400, "请先在 AI 设置中配置 API Key"); return c;
    }

    private Long currentUserId() { return CurrentUserContext.get() == null ? null : CurrentUserContext.get().id(); }
    private StoredConfig readUser(Long id) { return jdbc.query("SELECT base_url, model, api_key_ciphertext FROM ai_user_configs WHERE user_id = ?", rs -> rs.next() ? new StoredConfig(rs.getString(1), rs.getString(2), decrypt(rs.getString(3))) : null, id); }
    private void writeUser(Long id, StoredConfig c) { String encrypted = encrypt(c.apiKey()); int n = jdbc.update("UPDATE ai_user_configs SET base_url = ?, model = ?, api_key_ciphertext = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", c.baseUrl(), c.model(), encrypted, id); if (n == 0) jdbc.update("INSERT INTO ai_user_configs (user_id, base_url, model, api_key_ciphertext) VALUES (?, ?, ?, ?)", id, c.baseUrl(), c.model(), encrypted); }
    private StoredConfig readFile() { if (!Files.exists(configPath)) return null; try { return objectMapper.readValue(Files.readString(configPath), StoredConfig.class); } catch (IOException | RuntimeException ex) { throw new BusinessException(500, "AI 配置文件损坏，请重新配置"); } }
    private void writeFile(StoredConfig c) { try { Files.createDirectories(configPath.getParent()); Files.writeString(configPath, objectMapper.writeValueAsString(c)); } catch (IOException ex) { throw new BusinessException(500, "AI 配置保存失败"); } }
    private String encrypt(String value) { try { byte[] nonce = new byte[12]; random.nextBytes(nonce); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce)); byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array()); } catch (Exception ex) { throw new BusinessException(500, "AI 配置加密失败"); } }
    private String decrypt(String value) { try { ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(value)); byte[] nonce = new byte[12]; b.get(nonce); byte[] data = new byte[b.remaining()]; b.get(data); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce)); return new String(cipher.doFinal(data), StandardCharsets.UTF_8); } catch (Exception ex) { throw new BusinessException(500, "AI 配置无法解密，请重新配置"); } }
    private String mask(String key) { if (key == null || key.isBlank()) return ""; return key.length() <= 8 ? "••••••••" : key.substring(0, 4) + "••••" + key.substring(key.length() - 4); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record StoredConfig(String baseUrl, String model, String apiKey) { }
}
