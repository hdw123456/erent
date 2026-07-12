package com.example.aigateway.security;

/** Authenticated identity attached to gateway API key requests. */
public class ApiKeyPrincipal {
    private final Long apiKeyId;
    private final Long userId;
    private final String prefix;

    public ApiKeyPrincipal(Long apiKeyId, Long userId, String prefix) {
        this.apiKeyId = apiKeyId;
        this.userId = userId;
        this.prefix = prefix;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPrefix() {
        return prefix;
    }
}
