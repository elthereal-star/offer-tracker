package com.offertracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.security.MessageDigest;

@Service
public class JwtTokenService {
    public static final long ACCESS_TOKEN_SECONDS = 900;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper; private final byte[] secret;
    public JwtTokenService(ObjectMapper objectMapper, @Value("${offer-tracker.auth.jwt-secret}") String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters");
        this.objectMapper = objectMapper; this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public String createAccessToken(Long userId, String role) {
        long now = Instant.now().getEpochSecond(); String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encode(Map.of("sub", userId.toString(), "role", role, "iat", now, "exp", now + ACCESS_TOKEN_SECONDS));
        return header + "." + payload + "." + sign(header + "." + payload);
    }
    public AccessClaims verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.US_ASCII), sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            Map<?, ?> payload = objectMapper.readValue(DECODER.decode(parts[1]), Map.class);
            long exp = ((Number) payload.get("exp")).longValue(); if (exp <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            return new AccessClaims(Long.valueOf((String) payload.get("sub")), (String) payload.get("role"), exp);
        } catch (Exception ex) { throw new BusinessException(401, "访问令牌无效或已过期"); }
    }
    private String encode(Object value) { try { return B64.encodeToString(objectMapper.writeValueAsBytes(value)); } catch (Exception ex) { throw new IllegalStateException("生成访问令牌失败", ex); } }
    private String sign(String content) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return B64.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException("签名访问令牌失败", ex); } }
    public record AccessClaims(Long userId, String role, long expiresAt) {}
}
