package com.offertracker.config;

import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.common.ApiResponse;
import com.offertracker.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthenticationContextFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final ObjectMapper objectMapper;
    private final boolean authRequired;
    public AuthenticationContextFilter(JwtTokenService tokens, ObjectMapper objectMapper,
                                       @Value("${offer-tracker.auth.required:false}") boolean authRequired) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.authRequired = authRequired;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header != null) {
                if (!header.startsWith("Bearer ") || header.substring(7).trim().isBlank()) {
                    writeUnauthorized(response, "访问令牌格式无效");
                    return;
                }
                JwtTokenService.AccessClaims claims = tokens.verify(header.substring(7).trim());
                CurrentUserContext.set(new CurrentUser(claims.userId(), claims.role()));
            } else if (authRequired && requiresAuthentication(request)) {
                writeUnauthorized(response, "请先登录");
                return;
            }
            chain.doFilter(request, response);
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
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) == false
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
