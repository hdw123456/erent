package com.example.aigateway.service;

import com.example.aigateway.dto.request.CreateProviderKeyRequest;
import com.example.aigateway.dto.request.UpdateProviderKeyRequest;
import com.example.aigateway.dto.response.ProviderKeyResponse;
import com.example.aigateway.entity.ProviderKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import com.example.aigateway.security.ProviderKeyCrypto;
import com.example.aigateway.security.SensitiveDataMasker;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates, encrypts, and manages user-owned provider keys. */
@Service
public class ProviderKeyService {
    private static final Logger logger = LoggerFactory.getLogger(ProviderKeyService.class);
    private static final String DEFAULT_PROVIDER_KEY_TYPE = "OFFICIAL_API_KEY";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ProviderKeyMapper providerKeyMapper;
    private final ProviderKeyCrypto providerKeyCrypto;

    public ProviderKeyService(ProviderKeyMapper providerKeyMapper, ProviderKeyCrypto providerKeyCrypto) {
        this.providerKeyMapper = providerKeyMapper;
        this.providerKeyCrypto = providerKeyCrypto;
    }

    public ProviderKeyResponse saveProviderKey(CreateProviderKeyRequest request, long userId) {
        validateProviderKey(request.getProviderId(), request.getRawProviderKey());
        String encryptedKey = providerKeyCrypto.encrypt(request.getRawProviderKey());
        ProviderKey providerKey = new ProviderKey(
                request.getProviderId(),
                userId,
                encryptedKey,
                SensitiveDataMasker.maskSecret(request.getRawProviderKey()));
        providerKey.setProviderKeyType(normalizeProviderKeyType(request.getProviderKeyType()));
        providerKey.setBaseUrl(normalizeBlank(request.getBaseUrl()));
        providerKey.setPriority(normalizePriority(request.getPriority()));
        providerKeyMapper.insertProviderKey(providerKey);
        logger.info("Provider key saved, userId={}, providerId={}, providerKeyId={}, keyHint={}, type={}",
                userId, request.getProviderId(), providerKey.getId(), providerKey.getKeyHint(),
                providerKey.getProviderKeyType());
        return toResponse(providerKey);
    }

    public ProviderKeyResponse saveProviderKey(Long providerId, String rawProviderKey, long userId) {
        CreateProviderKeyRequest request = new CreateProviderKeyRequest();
        request.setProviderId(providerId);
        request.setRawProviderKey(rawProviderKey);
        return saveProviderKey(request, userId);
    }

    public ProviderKeyResponse updateProviderKey(Long id, UpdateProviderKeyRequest request, long userId) {
        if (id == null) {
            throw new BusinessException("PROVIDER_KEY_ID_REQUIRED", "Provider key id is required");
        }
        ProviderKey existingProviderKey = requireOwnedProviderKey(id, userId);

        if (hasText(request.getRawProviderKey())) {
            String encryptedKey = providerKeyCrypto.encrypt(request.getRawProviderKey());
            existingProviderKey.setEncryptedKey(encryptedKey);
            existingProviderKey.setKeyHint(SensitiveDataMasker.maskSecret(request.getRawProviderKey()));
            if (request.getEnabled() == null) {
                existingProviderKey.setEnabled(true);
            }
            if (request.getSchedulable() == null) {
                existingProviderKey.setSchedulable(true);
            }
        }
        if (hasText(request.getProviderKeyType())) {
            existingProviderKey.setProviderKeyType(normalizeProviderKeyType(request.getProviderKeyType()));
        }
        if (request.getBaseUrl() != null) {
            existingProviderKey.setBaseUrl(normalizeBlank(request.getBaseUrl()));
        }
        if (request.getEnabled() != null) {
            existingProviderKey.setEnabled(request.getEnabled());
        }
        if (request.getSchedulable() != null) {
            existingProviderKey.setSchedulable(request.getSchedulable());
        }
        if (request.getPriority() != null) {
            existingProviderKey.setPriority(normalizePriority(request.getPriority()));
        }

        existingProviderKey.setStatus(STATUS_ACTIVE);
        existingProviderKey.setRateLimitedUntil(null);
        existingProviderKey.setOverloadedUntil(null);
        existingProviderKey.setTempDisabledUntil(null);
        existingProviderKey.setLastErrorCode(null);
        existingProviderKey.setLastErrorMessage(null);
        providerKeyMapper.updateProviderKey(existingProviderKey);
        logger.info("Provider key updated, userId={}, providerId={}, providerKeyId={}, keyHint={}, type={}",
                userId, existingProviderKey.getProviderId(), existingProviderKey.getId(),
                existingProviderKey.getKeyHint(), existingProviderKey.getProviderKeyType());
        return toResponse(existingProviderKey);
    }

    public ProviderKeyResponse updateProviderKey(Long id, String rawProviderKey, long userId) {
        UpdateProviderKeyRequest request = new UpdateProviderKeyRequest();
        request.setRawProviderKey(rawProviderKey);
        return updateProviderKey(id, request, userId);
    }

    private ProviderKey requireOwnedProviderKey(Long id, long userId) {
        if (id == null) {
            throw new BusinessException("PROVIDER_KEY_ID_REQUIRED", "Provider key id is required");
        }
        ProviderKey existingProviderKey = providerKeyMapper.getProviderKeyById(id);
        if (existingProviderKey == null) {
            throw new BusinessException("PROVIDER_KEY_NOT_FOUND", "Provider key not found", HttpStatus.NOT_FOUND);
        }
        if (existingProviderKey.getUserId() == null || !existingProviderKey.getUserId().equals(userId)) {
            throw new BusinessException("PROVIDER_KEY_FORBIDDEN", "Provider key does not belong to current user", HttpStatus.FORBIDDEN);
        }
        return existingProviderKey;
    }

    private void validateProviderKey(Long providerId, String rawProviderKey) {
        if (providerId == null) {
            throw new BusinessException("PROVIDER_ID_REQUIRED", "Provider id is required");
        }
        if (rawProviderKey == null || rawProviderKey.isBlank()) {
            throw new BusinessException("PROVIDER_KEY_REQUIRED", "Provider key is required");
        }
    }

    private String normalizeProviderKeyType(String providerKeyType) {
        if (!hasText(providerKeyType)) {
            return DEFAULT_PROVIDER_KEY_TYPE;
        }
        return providerKeyType.trim().toUpperCase(Locale.ROOT);
    }

    private Integer normalizePriority(Integer priority) {
        return priority == null ? 100 : Math.max(0, priority);
    }

    private String normalizeBlank(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ProviderKeyResponse toResponse(ProviderKey providerKey) {
        ProviderKeyResponse response = new ProviderKeyResponse();
        response.setId(providerKey.getId());
        response.setProviderId(providerKey.getProviderId());
        response.setProviderKeyType(providerKey.getProviderKeyType());
        response.setBaseUrl(providerKey.getBaseUrl());
        response.setKeyHint(providerKey.getKeyHint());
        response.setEnabled(providerKey.getEnabled());
        response.setStatus(providerKey.getStatus());
        response.setSchedulable(providerKey.getSchedulable());
        response.setPriority(providerKey.getPriority());
        response.setRateLimitedUntil(providerKey.getRateLimitedUntil());
        response.setOverloadedUntil(providerKey.getOverloadedUntil());
        response.setTempDisabledUntil(providerKey.getTempDisabledUntil());
        response.setExpiresAt(providerKey.getExpiresAt());
        response.setLastErrorCode(providerKey.getLastErrorCode());
        response.setLastErrorMessage(providerKey.getLastErrorMessage());
        response.setLastUsedAt(providerKey.getLastUsedAt());
        response.setLastSuccessAt(providerKey.getLastSuccessAt());
        response.setLastFailedAt(providerKey.getLastFailedAt());
        return response;
    }
}
