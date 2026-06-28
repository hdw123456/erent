package com.example.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateProviderKeyRequest {
    @NotNull
    private Long providerId;

    @NotBlank
    @Size(max = 512)
    private String rawProviderKey;

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
}
