package com.example.aigateway.service;

import com.example.aigateway.dto.response.CreateApiKeyResponse;
import com.example.aigateway.entity.ApiKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ApiKeyMapper;
import com.example.aigateway.security.ApiKeyHasher;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {
    private static final int API_KEY_PREFIX_LENGTH = 8;
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, ApiKeyHasher apiKeyHasher) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyHasher = apiKeyHasher;
    }

    public CreateApiKeyResponse createApiKey(long userId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("API_KEY_NAME_REQUIRED", "API key name is required");
        }

        String rawApiKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        String prefix = rawApiKey.substring(0, API_KEY_PREFIX_LENGTH);
        String keyHash = apiKeyHasher.hash(rawApiKey);
        ApiKey newApiKey = new ApiKey(keyHash, userId, name, prefix);
        apiKeyMapper.insertApiKey(newApiKey, userId);

        CreateApiKeyResponse response = new CreateApiKeyResponse();
        response.setId(newApiKey.getId());
        response.setName(newApiKey.getName());
        response.setPrefix(newApiKey.getPrefix());
        response.setApiKey(rawApiKey);
        response.setEnabled(newApiKey.isEnabled());
        logger.info("API key created, userId={}, apiKeyId={}, prefix={}", userId, newApiKey.getId(), prefix);
        return response;
    }

    public ApiKey updateApiKey(long userId, long apiKeyId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("API_KEY_NAME_REQUIRED", "API key name is required");
        }
        ApiKey apiKey = getOwnedApiKey(userId, apiKeyId);
        apiKey.setName(name);
        apiKeyMapper.updateApiKey(apiKey);
        logger.info("API key updated, userId={}, apiKeyId={}", userId, apiKeyId);
        return apiKey;
    }

    public void disableApiKey(long userId, long apiKeyId) {
        ApiKey apiKey = getOwnedApiKey(userId, apiKeyId);
        apiKey.setEnabled(false);
        apiKeyMapper.updateApiKey(apiKey);
        logger.info("API key disabled, userId={}, apiKeyId={}", userId, apiKeyId);
    }

    public List<ApiKey> getUserApiKeys(long userId) {
        return apiKeyMapper.getUserApi(userId);
    }

    private ApiKey getOwnedApiKey(long userId, long apiKeyId) {
        ApiKey apiKey = apiKeyMapper.getApiKeyById(apiKeyId);
        if (apiKey == null) {
            logger.warn("API key not found, apiKeyId={}", apiKeyId);
            throw new BusinessException("API_KEY_NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        if (apiKey.getUserId() == null || !apiKey.getUserId().equals(userId)) {
            logger.warn("API key ownership check failed, userId={}, apiKeyId={}", userId, apiKeyId);
            throw new BusinessException("API_KEY_FORBIDDEN", "API key does not belong to current user", HttpStatus.FORBIDDEN);
        }
        return apiKey;
    }
}
