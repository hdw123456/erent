package com.example.aigateway.entity;

import java.math.BigDecimal;
import java.util.Date;

/** Persistence model for usage record data. */
public class UsageRecord {
    private Long id;
    private String requestId;
    private Long userId;
    private Long modelId;
    private Long providerKeyId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String usageSource = "PROVIDER";
    private BigDecimal costAmount;
    private Date createdAt;

    public UsageRecord() {
    }

    public UsageRecord(String requestId, Long userId, Long modelId, Integer inputTokens,
                       Integer outputTokens, Integer totalTokens, BigDecimal costAmount) {
        this.requestId = requestId;
        this.userId = userId;
        this.modelId = modelId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.costAmount = costAmount;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getProviderKeyId() {
        return providerKeyId;
    }

    public void setProviderKeyId(Long providerKeyId) {
        this.providerKeyId = providerKeyId;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getUsageSource() {
        return usageSource;
    }

    public void setUsageSource(String usageSource) {
        this.usageSource = usageSource;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
