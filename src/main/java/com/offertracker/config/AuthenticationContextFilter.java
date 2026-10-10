package com.offertracker.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.ApiResponse;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthenticationContextFilter extends OncePerRequestFilter {
    private final UserMapper users;
    private final ObjectMapper objectMapper;
    private final boolean authRequired;

    public AuthenticationContextFilter(UserMapper users, ObjectMapper objectMapper,
                                       @Value("${offer-tracker.auth.required:false}") boolean authRequired) {
        this.users = users;
        this.objectMapper = objectMapper;
        this.authRequired = authRequired;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        try {
            if (authorization != null) {
                if (!authorization.startsWith("Bearer ") || authorization.substring(7).trim().isBlank()) {
                    writeUnauthorized(response, "访问令牌格式无效");
                    return;
                }
                String token = authorization.substring(7).trim();
                Object loginId = StpUtil.getLoginIdByToken(token);
                User user = users.selectById(Long.valueOf(String.valueOf(loginId)));
                if (user == null || !"ACTIVE".equals(user.getStatus())) {
                    StpUtil.logoutByTokenValue(token);
                    writeUnauthorized(response, "账户当前不可用");
                    return;
                }
                CurrentUserContext.set(new CurrentUser(user.getId(), user.getRole()));
            } else if (authRequired && requiresAuthentication(request)) {
                writeUnauthorized(response, "请先登录");
                return;
            }
            chain.doFilter(request, response);
        } catch (NotLoginException | NumberFormatException ex) {
            writeUnauthorized(response, "访问令牌无效或已过期");
        } catch (BusinessException ex) {
            if (ex.getCode() == 401) {
                writeUnauthorized(response, ex.getMessage());
                return;
            }
            throw ex;
        } finally {
            CurrentUserContext.clear();
        }
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"OPTIONS".equalsIgnoreCase(request.getMethod())
                && path.startsWith("/api/")
                && !path.startsWith("/api/auth/");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, message)));
    }
}
