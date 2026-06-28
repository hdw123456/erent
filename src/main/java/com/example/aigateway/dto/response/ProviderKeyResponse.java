package com.example.aigateway.dto.response;

public class ProviderKeyResponse {
    private Long id;
    private Long providerId;
    private String keyHint;
    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getKeyHint() {
        return keyHint;
    }

    public void setKeyHint(String keyHint) {
        this.keyHint = keyHint;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
