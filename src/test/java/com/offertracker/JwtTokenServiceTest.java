package com.offertracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.service.JwtTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {
    private final JwtTokenService tokens = new JwtTokenService(new ObjectMapper(), "test-only-jwt-secret-at-least-32-characters");

    @Test
    void signsAndVerifiesAccessToken() {
        String token = tokens.createAccessToken(42L, "USER");
        JwtTokenService.AccessClaims claims = tokens.verify(token);
        assertEquals(42L, claims.userId());
        assertEquals("USER", claims.role());
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokens.createAccessToken(42L, "USER");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        assertThrows(BusinessException.class, () -> tokens.verify(tampered));
    }
}
