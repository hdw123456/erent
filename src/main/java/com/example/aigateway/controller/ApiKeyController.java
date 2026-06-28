package com.example.aigateway.controller;

import java.util.List;

import com.example.aigateway.dto.request.CreateApiKeyRequest;
import com.example.aigateway.dto.request.UpdateApiKeyRequest;
import com.example.aigateway.dto.response.ApiKeyResponse;
import com.example.aigateway.dto.response.CreateApiKeyResponse;
import com.example.aigateway.entity.ApiKey;
import com.example.aigateway.service.ApiKeyService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PatchMapping("/{id}/disable")
    public void disableApiKey(@RequestHeader("X-User-Id") Long userId, @PathVariable long id) {
        apiKeyService.disableApiKey(userId, id);
    }

    @PatchMapping("/{id}")
    public ApiKeyResponse updateApiKey(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable long id,
            @Valid @RequestBody UpdateApiKeyRequest request
    ) {
        return toResponse(apiKeyService.updateApiKey(userId, id, request.getName()));
    }

    @PostMapping
    public CreateApiKeyResponse createApiKey(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.createApiKey(userId, request.getName());
    }

    @GetMapping
    public List<ApiKeyResponse> getUserApiKeyByUserId(@RequestHeader("X-User-Id") Long userId) {
        List<ApiKey> apiKeys = apiKeyService.getUserApiKeys(userId);
        List<ApiKeyResponse> apiKeyResponses = new ArrayList<>();
        for (ApiKey apiKey : apiKeys) {
            apiKeyResponses.add(toResponse(apiKey));
        }
        return apiKeyResponses;
    }

    private ApiKeyResponse toResponse(ApiKey apiKey) {
        ApiKeyResponse apiKeyResponse = new ApiKeyResponse();
        apiKeyResponse.setId(apiKey.getId());
        apiKeyResponse.setName(apiKey.getName());
        apiKeyResponse.setPrefix(apiKey.getPrefix());
        apiKeyResponse.setEnabled(apiKey.isEnabled());
        apiKeyResponse.setCreatedAt(apiKey.getCreatedAt() == null ? null : apiKey.getCreatedAt().toString());
        apiKeyResponse.setLastUsedAt(apiKey.getLastUsedAt() != null ? apiKey.getLastUsedAt().toString() : null);
        return apiKeyResponse;
    }
}
