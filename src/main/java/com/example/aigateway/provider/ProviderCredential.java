package com.example.aigateway.provider;

import com.example.aigateway.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Decrypted, call-scoped upstream credential and endpoint settings. */
public class ProviderCredential {
    private Long providerKeyId;
    private String providerKeyType;
    private Long providerId;
    private String providerCode;
    private String apiKey;
    private String baseUrl;
    private Map<String, String> headers = new LinkedHashMap<>();

    public ProviderCredential() {
    }

    public ProviderCredential(Long providerId, String providerCode, String apiKey, String baseUrl) {
        this(null, null, providerId, providerCode, apiKey, baseUrl);
    }

    public ProviderCredential(
            Long providerKeyId,
            String providerKeyType,
            Long providerId,
            String providerCode,
            String apiKey,
            String baseUrl) {
        this.providerKeyId = providerKeyId;
        this.providerKeyType = providerKeyType;
        this.providerId = providerId;
        this.providerCode = providerCode;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(
                    "PROVIDER_API_KEY_REQUIRED",
                    "Provider API key is required",
                    HttpStatus.BAD_GATEWAY
            );
        }
        return apiKey;
    }

    public String requireBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(
                    "PROVIDER_BASE_URL_REQUIRED",
                    "Provider base URL is required",
                    HttpStatus.BAD_GATEWAY
            );
        }
        return baseUrl;
    }

    public Long getProviderKeyId() {
        return providerKeyId;
    }

    public void setProviderKeyId(Long providerKeyId) {
        this.providerKeyId = providerKeyId;
    }

    public String getProviderKeyType() {
        return providerKeyType;
    }

    public void setProviderKeyType(String providerKeyType) {
        this.providerKeyType = providerKeyType;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }
}
