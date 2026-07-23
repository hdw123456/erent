package com.example.aigateway.entity;

import java.util.Date;

/** Persistence model for usage billing dedup data. */
public class UsageBillingDedup {
    private Long id;
    private String requestId;
    private Long apiKeyId;
    private String requestFingerprint;
    private Date createdAt;

    public UsageBillingDedup() {
    }

    public UsageBillingDedup(String requestId, Long apiKeyId, String requestFingerprint) {
        this.requestId = requestId;
        this.apiKeyId = apiKeyId;
        this.requestFingerprint = requestFingerprint;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
