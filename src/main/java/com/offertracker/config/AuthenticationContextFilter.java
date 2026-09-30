package com.offertracker.config;

import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthenticationContextFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    public AuthenticationContextFilter(JwtTokenService tokens) { this.tokens = tokens; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header != null && header.startsWith("Bearer ")) {
                JwtTokenService.AccessClaims claims = tokens.verify(header.substring(7).trim());
                CurrentUserContext.set(new CurrentUser(claims.userId(), claims.role()));
            }
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
