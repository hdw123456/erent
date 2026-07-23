package com.example.aigateway.security;

import com.example.aigateway.common.ErrorResponse;
import com.example.aigateway.entity.ApiKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates gateway requests from platform API key headers. */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isGatewayPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawApiKey = extractApiKey(request);
        if (rawApiKey == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "API key is required");
            return;
        }

        try {
            ApiKey apiKey = apiKeyService.authenticateRawApiKey(rawApiKey);
            ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKey.getId(), apiKey.getUserId(), apiKey.getPrefix());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeError(response, exception.getStatus().value(), exception.getCode(), exception.getMessage());
        }
    }

    private boolean isGatewayPath(String servletPath) {
        return servletPath.startsWith("/api/chat/")
                || servletPath.startsWith("/v1/")
                || servletPath.equals("/chat/completions")
                || servletPath.startsWith("/responses")
                || servletPath.startsWith("/backend-api/codex/");
    }

    private String extractApiKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return blankToNull(authorization.substring(BEARER_PREFIX.length()));
        }

        String apiKey = blankToNull(request.getHeader("x-api-key"));
        if (apiKey != null) {
            return apiKey;
        }

        return blankToNull(request.getHeader("x-goog-api-key"));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code, message, null)));
    }
}
