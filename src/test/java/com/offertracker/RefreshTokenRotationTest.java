package com.offertracker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.RefreshTokenRequest;
import com.offertracker.entity.AuthRefreshSession;
import com.offertracker.entity.User;
import com.offertracker.mapper.AuthRefreshSessionMapper;
import com.offertracker.mapper.UserMapper;
import com.offertracker.mapper.VerificationCodeMapper;
import com.offertracker.service.IdentityService;
import com.offertracker.service.JwtTokenService;
import com.offertracker.service.SmsCodeSender;
import com.offertracker.service.SmsRequestLimiter;
import com.offertracker.service.VerificationCodeAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenRotationTest {
    @Test
    void doesNotIssueTokensWhenAnotherRequestAlreadyConsumedRefreshSession() {
        UserMapper users = mock(UserMapper.class);
        AuthRefreshSessionMapper sessions = mock(AuthRefreshSessionMapper.class);
        AuthRefreshSession session = new AuthRefreshSession();
        session.setId(21L);
        session.setUserId(9L);
        session.setTokenHash(hash("old-refresh-token"));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session.setDeviceLabel("browser");
        User user = new User();
        user.setId(9L);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        when(sessions.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        when(users.selectById(9L)).thenReturn(user);
        when(sessions.update(isNull(), any())).thenReturn(0);

        IdentityService identity = new IdentityService(users, sessions, mock(VerificationCodeMapper.class),
                mock(VerificationCodeAttemptService.class), mock(SmsRequestLimiter.class), mock(SmsCodeSender.class),
                new JwtTokenService(new ObjectMapper(), "test-jwt-secret-with-at-least-32-characters", false));

        BusinessException error = assertThrows(BusinessException.class,
                () -> identity.refresh(new RefreshTokenRequest("old-refresh-token")));

        assertEquals(401, error.getCode());
        verify(sessions).update(isNull(), any());
        verify(sessions, never()).insert(any(AuthRefreshSession.class));
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
