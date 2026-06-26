package com.example.aigateway.entity;


public class UserApiKey {
    private UserAccount userAccount;
    private int apiKeyCount;

    public UserApiKey() {
    }

    public UserApiKey(UserAccount userAccount, int apiKeyCount) {
        this.userAccount = userAccount;
        this.apiKeyCount = apiKeyCount;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public int getApiKeyCount() {
        return apiKeyCount;
    }

    public void setApiKeyCount(int apiKeyCount) {
        this.apiKeyCount = apiKeyCount;
    }
    
}
