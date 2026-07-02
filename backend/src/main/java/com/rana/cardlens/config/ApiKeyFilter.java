package com.rana.cardlens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guardrail: every /api/** endpoint requires a static API key in the
 * X-Api-Key header. The expected value comes from an env var only.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";

    private final String expectedKey;

    public ApiKeyFilter(@Value("${cardlens.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard the API surface; leave actuator/health unguarded.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String provided = request.getHeader(HEADER);
        if (expectedKey == null || expectedKey.isBlank() || !expectedKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
