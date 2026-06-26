package com.example.aigateway.dto;

import java.math.BigDecimal;

public class LowBalanceUser {
    private Long userId;
    private String username;
    private BigDecimal balance;
    private Long enabledApiKeyCount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getEnabledApiKeyCount() {
        return enabledApiKeyCount;
    }

    public void setEnabledApiKeyCount(Long enabledApiKeyCount) {
        this.enabledApiKeyCount = enabledApiKeyCount;
    }
}
