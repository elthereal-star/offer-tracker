package com.offertracker.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.AuthTokenResponse;
import com.offertracker.dto.LoginRequest;
import com.offertracker.dto.RegisterRequest;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class IdentityService {
    private final UserMapper users;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    public IdentityService(UserMapper users, LoginAttemptLimiter loginAttemptLimiter) {
        this.users = users;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String phone = request.phone().trim();
        if (users.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone)) > 0) {
            throw new BusinessException(409, "手机号已注册");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
        return startSession(user);
    }

    public AuthTokenResponse login(LoginRequest request) {
        String phone = request.phone().trim();
        loginAttemptLimiter.checkAllowed(phone);
        User user = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || user.getPasswordHash() == null
                || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "手机号或密码错误");
        }
        loginAttemptLimiter.clear(phone);
        return startSession(user);
    }

    public void logout(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    private AuthTokenResponse startSession(User user) {
        StpUtil.login(user.getId());
        return new AuthTokenResponse("Bearer", StpUtil.getTokenValue(), StpUtil.getTokenTimeout());
    }
}
