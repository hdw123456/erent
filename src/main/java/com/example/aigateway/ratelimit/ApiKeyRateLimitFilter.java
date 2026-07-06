package com.example.aigateway.ratelimit;

import com.example.aigateway.common.ErrorResponse;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter rateLimiter;
    private final GatewayRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public ApiKeyRateLimitFilter(
            FixedWindowRateLimiter rateLimiter,
            GatewayRateLimitProperties properties,
            ObjectMapper objectMapper
    ) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        return !isGatewayPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        int windowSeconds = properties.getWindowSeconds();
        List<RateLimitDecision> decisions = List.of(
                rateLimiter.check("apiKey", String.valueOf(principal.getApiKeyId()), properties.getApiKeyLimit(), windowSeconds),
                rateLimiter.check("user", String.valueOf(principal.getUserId()), properties.getUserLimit(), windowSeconds),
                rateLimiter.check("ip", clientIp(request), properties.getIpLimit(), windowSeconds)
        );

        for (RateLimitDecision decision : decisions) {
            if (!decision.allowed()) {
                writeRateLimitError(response, decision);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private boolean isGatewayPath(String servletPath) {
        return servletPath.startsWith("/api/chat/")
                || servletPath.startsWith("/v1/")
                || servletPath.equals("/chat/completions")
                || servletPath.startsWith("/responses")
                || servletPath.startsWith("/backend-api/codex/");
    }

    private void writeRateLimitError(
            HttpServletResponse response,
            RateLimitDecision decision
    ) throws IOException {
        int retryAfterSeconds = retryAfterSeconds(decision.windowSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(
                "TOO_MANY_REQUESTS",
                "Rate limit exceeded",
                Map.of(
                        "dimension", decision.dimension(),
                        "limit", decision.limit(),
                        "windowSeconds", decision.windowSeconds(),
                        "current", decision.current(),
                        "retryAfterSeconds", retryAfterSeconds
                )
        )));
    }

    private int retryAfterSeconds(int windowSeconds) {
        if (windowSeconds <= 0) {
            return 1;
        }
        long now = Instant.now().getEpochSecond();
        long elapsedInWindow = now % windowSeconds;
        return (int) Math.max(1, windowSeconds - elapsedInWindow);
    }
}
