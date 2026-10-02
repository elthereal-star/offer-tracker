package com.offertracker;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.dto.LoginRequest;
import com.offertracker.dto.RegisterRequest;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.IdentityService;
import com.offertracker.service.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final LoginAttemptLimiter attempts = mock(LoginAttemptLimiter.class);
    private final IdentityService identity = new IdentityService(users, attempts);

    @Test
    void registersPhoneAndPasswordWithoutSmsAndStartsSaTokenSession() {
        when(users.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(users.insert(any(User.class))).thenAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setId(42L);
            return 1;
        });

        try (MockedStatic<StpUtil> stp = mockSession()) {
            var response = identity.register(new RegisterRequest("+8613800000001", "correct horse 7"));

            assertEquals("Bearer", response.tokenType());
            assertEquals("test-token", response.accessToken());
            assertEquals(2592000L, response.expiresInSeconds());
            var user = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(users).insert(user.capture());
            assertNotEquals("correct horse 7", user.getValue().getPasswordHash());
            assertTrue(new BCryptPasswordEncoder(12).matches("correct horse 7", user.getValue().getPasswordHash()));
            stp.verify(() -> StpUtil.login(42L));
        }
    }

    @Test
    void logsInWithExistingPhoneAndPassword() {
        User user = new User();
        user.setId(7L);
        user.setPhone("+8613800000002");
        user.setPasswordHash(new BCryptPasswordEncoder(12).encode("correct horse 8"));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        try (MockedStatic<StpUtil> stp = mockSession()) {
            var response = identity.login(new LoginRequest(user.getPhone(), "correct horse 8"));

            assertEquals("test-token", response.accessToken());
            verify(attempts).clear(user.getPhone());
            stp.verify(() -> StpUtil.login(7L));
        }
    }

    private MockedStatic<StpUtil> mockSession() {
        MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class);
        stp.when(() -> StpUtil.getTokenValue()).thenReturn("test-token");
        stp.when(() -> StpUtil.getTokenTimeout()).thenReturn(2592000L);
        return stp;
    }
}
