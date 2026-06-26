package com.example.aigateway.dto;

import java.math.BigDecimal;

public class ModelUsageStats {
    private Long modelId;
    private String modelCode;
    private String modelDisplayName;
    private Long requestCount;
    private Long errorCount;
    private BigDecimal errorRate;
    private BigDecimal avgLatencyMs;

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelDisplayName() {
        return modelDisplayName;
    }

    public void setModelDisplayName(String modelDisplayName) {
        this.modelDisplayName = modelDisplayName;
    }

    public Long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Long requestCount) {
        this.requestCount = requestCount;
    }

    public Long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Long errorCount) {
        this.errorCount = errorCount;
    }

    public BigDecimal getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(BigDecimal errorRate) {
        this.errorRate = errorRate;
    }

    public BigDecimal getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(BigDecimal avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }
}
