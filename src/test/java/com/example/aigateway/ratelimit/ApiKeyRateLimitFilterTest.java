package com.example.aigateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.aigateway.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyRateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatRequestShouldReturnTooManyRequestsWhenAnyDimensionIsBlocked() throws Exception {
        FixedWindowRateLimiter rateLimiter = org.mockito.Mockito.mock(FixedWindowRateLimiter.class);
        when(rateLimiter.check(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(RateLimitDecision.allowed("apiKey", "10", 1, 60, 60))
                .thenReturn(RateLimitDecision.blocked("user", "20", 121, 120, 60))
                .thenReturn(RateLimitDecision.allowed("ip", "127.0.0.1", 1, 300, 60));
        ApiKeyRateLimitFilter filter = new ApiKeyRateLimitFilter(rateLimiter, properties(), objectMapper());
        MockHttpServletRequest request = request("/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ApiKeyPrincipal(10L, 20L, "ak_test"),
                null
        ));

        filter.doFilter(request, response, shouldNotBeCalled());

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("TOO_MANY_REQUESTS"));
        assertTrue(response.getContentAsString().contains("\"dimension\":\"user\""));
    }

    @Test
    void chatRequestShouldContinueWhenAllDimensionsAreAllowed() throws Exception {
        FixedWindowRateLimiter rateLimiter = org.mockito.Mockito.mock(FixedWindowRateLimiter.class);
        when(rateLimiter.check(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(RateLimitDecision.allowed("apiKey", "10", 1, 60, 60))
                .thenReturn(RateLimitDecision.allowed("user", "20", 1, 120, 60))
                .thenReturn(RateLimitDecision.allowed("ip", "127.0.0.1", 1, 300, 60));
        ApiKeyRateLimitFilter filter = new ApiKeyRateLimitFilter(rateLimiter, properties(), objectMapper());
        MockHttpServletRequest request = request("/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ApiKeyPrincipal(10L, 20L, "ak_test"),
                null
        ));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

        assertEquals(204, response.getStatus());
    }

    private GatewayRateLimitProperties properties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setApiKeyLimit(60);
        properties.setUserLimit(120);
        properties.setIpLimit(300);
        return properties;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private FilterChain shouldNotBeCalled() {
        return (request, response) -> {
            throw new AssertionError("Filter chain should not be called");
        };
    }
}
