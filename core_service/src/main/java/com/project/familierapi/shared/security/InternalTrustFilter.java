package com.project.familierapi.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalTrustFilter extends OncePerRequestFilter {

    @Value("${application.security.internal.secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Paths bypass hoàn toàn — không cần secret
        if (path.startsWith("/api/v1/auth/") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/actuator") ||
                path.startsWith("/api/v1/suggestions/urgent/stream")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestSecret = request.getHeader("X-Internal-Secret");

        // Có secret hợp lệ — internal call, cho qua luôn không cần JWT
        if (requestSecret != null && requestSecret.equals(internalSecret)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Không có secret — request từ mobile/gateway, tiếp tục chuỗi filter bình thường
        // JwtAuthenticationFilter sẽ xử lý authentication
        filterChain.doFilter(request, response);
    }
}
