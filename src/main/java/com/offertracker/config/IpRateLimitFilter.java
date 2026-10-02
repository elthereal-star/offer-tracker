package com.offertracker.config;

import com.offertracker.common.ApiResponse;
import com.offertracker.common.BusinessException;
import com.offertracker.service.IpRequestLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("production")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IpRateLimitFilter extends org.springframework.web.filter.OncePerRequestFilter {
    private final ClientIpResolver resolver;
    private final IpRequestLimiter limiter;
    private final ObjectMapper objectMapper;

    public IpRateLimitFilter(ClientIpResolver resolver, IpRequestLimiter limiter, ObjectMapper objectMapper) {
        this.resolver = resolver; this.limiter = limiter; this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/auth/") && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
            try { limiter.checkAllowed(resolver.resolve(request)); }
            catch (BusinessException ex) {
                response.setStatus(429); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(429, ex.getMessage()))); return;
            }
        }
        chain.doFilter(request, response);
    }
}
