package com.example.aigateway.dto.request;

import jakarta.validation.constraints.Size;

public class UpdateProviderKeyRequest {
    @Size(max = 2048)
    private String rawProviderKey;

    @Size(max = 32)
    private String providerKeyType;

    @Size(max = 255)
    private String baseUrl;

    private Boolean enabled;
    private Boolean schedulable;
    private Integer priority;

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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getSchedulable() {
        return schedulable;
    }

    public void setSchedulable(Boolean schedulable) {
        this.schedulable = schedulable;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
