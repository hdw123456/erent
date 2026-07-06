package com.example.aigateway.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProviderKeyQuotaWindow {
    private Long id;
    private Long providerKeyId;
    private String windowType;
    private BigDecimal quotaLimit;
    private BigDecimal quotaUsed;
    private LocalDateTime windowStartAt;
    private LocalDateTime resetAt;
    private String status;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderKeyId() {
        return providerKeyId;
    }

    public void setProviderKeyId(Long providerKeyId) {
        this.providerKeyId = providerKeyId;
    }

    public String getWindowType() {
        return windowType;
    }

    public void setWindowType(String windowType) {
        this.windowType = windowType;
    }

    public BigDecimal getQuotaLimit() {
        return quotaLimit;
    }

    public void setQuotaLimit(BigDecimal quotaLimit) {
        this.quotaLimit = quotaLimit;
    }

    public BigDecimal getQuotaUsed() {
        return quotaUsed;
    }

    public void setQuotaUsed(BigDecimal quotaUsed) {
        this.quotaUsed = quotaUsed;
    }

    public LocalDateTime getWindowStartAt() {
        return windowStartAt;
    }

    public void setWindowStartAt(LocalDateTime windowStartAt) {
        this.windowStartAt = windowStartAt;
    }

    public LocalDateTime getResetAt() {
        return resetAt;
    }

    public void setResetAt(LocalDateTime resetAt) {
        this.resetAt = resetAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
