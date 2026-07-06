package com.example.aigateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aigateway.entity.ApiKey;
import com.example.aigateway.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void v1ChatRequestWithoutApiKeyShouldReturnUnauthorized() throws Exception {
        ApiKeyService apiKeyService = org.mockito.Mockito.mock(ApiKeyService.class);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyService, objectMapper());
        MockHttpServletRequest request = request("/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, shouldNotBeCalled());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void v1ChatRequestWithValidApiKeyShouldAuthenticatePrincipal() throws Exception {
        ApiKeyService apiKeyService = org.mockito.Mockito.mock(ApiKeyService.class);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(10L);
        apiKey.setUserId(20L);
        apiKey.setPrefix("ak_test");
        when(apiKeyService.authenticateRawApiKey("ak_test_secret")).thenReturn(apiKey);

        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyService, objectMapper());
        MockHttpServletRequest request = request("/v1/chat/completions");
        request.addHeader("Authorization", "Bearer ak_test_secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
            assertInstanceOf(ApiKeyPrincipal.class, authentication.getPrincipal());
            ApiKeyPrincipal principal = (ApiKeyPrincipal) authentication.getPrincipal();
            assertEquals(10L, principal.getApiKeyId());
            assertEquals(20L, principal.getUserId());
        });

        assertEquals(200, response.getStatus());
        verify(apiKeyService).authenticateRawApiKey("ak_test_secret");
    }

    @Test
    void v1MessagesRequestWithXApiKeyShouldAuthenticatePrincipal() throws Exception {
        ApiKeyService apiKeyService = org.mockito.Mockito.mock(ApiKeyService.class);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(11L);
        apiKey.setUserId(21L);
        apiKey.setPrefix("ak_x");
        when(apiKeyService.authenticateRawApiKey("ak_x_secret")).thenReturn(apiKey);

        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyService, objectMapper());
        MockHttpServletRequest request = request("/v1/messages");
        request.addHeader("x-api-key", "ak_x_secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
            assertInstanceOf(ApiKeyPrincipal.class, authentication.getPrincipal());
            ApiKeyPrincipal principal = (ApiKeyPrincipal) authentication.getPrincipal();
            assertEquals(11L, principal.getApiKeyId());
            assertEquals(21L, principal.getUserId());
        });

        assertEquals(200, response.getStatus());
        verify(apiKeyService).authenticateRawApiKey("ak_x_secret");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
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
