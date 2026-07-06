package com.example.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateProviderKeyRequest {
    @NotNull
    private Long providerId;

    @NotBlank
    @Size(max = 2048)
    private String rawProviderKey;

    @Size(max = 32)
    private String providerKeyType;

    @Size(max = 255)
    private String baseUrl;

    private Integer priority;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getRawProviderKey() {
        return rawProviderKey;
    }

    public void setRawProviderKey(String rawProviderKey) {
        this.rawProviderKey = rawProviderKey;
    }

    public String getProviderKeyType() {
        return providerKeyType;
    }

    public void setProviderKeyType(String providerKeyType) {
        this.providerKeyType = providerKeyType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
