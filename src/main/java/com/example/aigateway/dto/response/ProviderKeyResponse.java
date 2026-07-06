package com.example.aigateway.dto.response;

import java.time.LocalDateTime;

public class ProviderKeyResponse {
    private Long id;
    private Long providerId;
    private String providerKeyType;
    private String baseUrl;
    private String keyHint;
    private Boolean enabled;
    private String status;
    private Boolean schedulable;
    private Integer priority;
    private LocalDateTime rateLimitedUntil;
    private LocalDateTime overloadedUntil;
    private LocalDateTime tempDisabledUntil;
    private LocalDateTime expiresAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime lastUsedAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailedAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getRateLimitedUntil() {
        return rateLimitedUntil;
    }

    public void setRateLimitedUntil(LocalDateTime rateLimitedUntil) {
        this.rateLimitedUntil = rateLimitedUntil;
    }

    public LocalDateTime getOverloadedUntil() {
        return overloadedUntil;
    }

    public void setOverloadedUntil(LocalDateTime overloadedUntil) {
        this.overloadedUntil = overloadedUntil;
    }

    public LocalDateTime getTempDisabledUntil() {
        return tempDisabledUntil;
    }

    public void setTempDisabledUntil(LocalDateTime tempDisabledUntil) {
        this.tempDisabledUntil = tempDisabledUntil;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(LocalDateTime lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }
}
