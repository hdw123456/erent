package com.example.aigateway.entity;

import java.util.Date;

public class ApiKey {
    // DB API Key
    private Long id;
    private String keyHash;
    private Long userId;
    private String name;
    private Date createdAt;
    private Date updatedAt;
    private Date lastUsedAt;
    private String prefix;
    private boolean enabled;

    public ApiKey() {
    }

    public ApiKey(String keyHash, Long userId, String name, String prefix) {
        this.keyHash = keyHash;
        this.userId = userId;
        this.name = name;
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.lastUsedAt = null;
        this.prefix = prefix;
        this.enabled = true;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Date getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Date lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
