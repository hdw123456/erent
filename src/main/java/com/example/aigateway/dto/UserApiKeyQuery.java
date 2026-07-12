package com.example.aigateway.dto;

/** Projection used for user api key query queries. */
public class UserApiKeyQuery {
    private Long userId;
    private String username;

    public UserApiKeyQuery() {
    }

    public UserApiKeyQuery(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

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
}
