package com.example.aigateway.service;

import com.example.aigateway.dto.response.ProviderKeyResponse;
import com.example.aigateway.entity.ProviderKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import com.example.aigateway.security.ProviderKeyCrypto;
import com.example.aigateway.security.SensitiveDataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderKeyService {
    private static final Logger logger = LoggerFactory.getLogger(ProviderKeyService.class);

    private final ProviderKeyMapper providerKeyMapper;
    private final ProviderKeyCrypto providerKeyCrypto;

    public ProviderKeyService(ProviderKeyMapper providerKeyMapper, ProviderKeyCrypto providerKeyCrypto) {
        this.providerKeyMapper = providerKeyMapper;
        this.providerKeyCrypto = providerKeyCrypto;
    }

    public ProviderKeyResponse saveProviderKey(Long providerId, String rawProviderKey, long userId) {
        validateProviderKey(providerId, rawProviderKey);
        String encryptedKey = providerKeyCrypto.encrypt(rawProviderKey);
        ProviderKey providerKey = new ProviderKey(providerId, userId, encryptedKey, SensitiveDataMasker.maskSecret(rawProviderKey));
        providerKeyMapper.insertProviderKey(providerKey);
        logger.info("Provider key saved, userId={}, providerId={}, providerKeyId={}, keyHint={}",
                userId, providerId, providerKey.getId(), providerKey.getKeyHint());
        return toResponse(providerKey);
    }

    public ProviderKeyResponse updateProviderKey(Long id, String rawProviderKey, long userId) {
        if (id == null) {
            throw new BusinessException("PROVIDER_KEY_ID_REQUIRED", "Provider key id is required");
        }
        if (rawProviderKey == null || rawProviderKey.isBlank()) {
            throw new BusinessException("PROVIDER_KEY_REQUIRED", "Provider key is required");
        }
        ProviderKey existingProviderKey = providerKeyMapper.getProviderKeyById(id);
        if (existingProviderKey == null) {
            throw new BusinessException("PROVIDER_KEY_NOT_FOUND", "Provider key not found", HttpStatus.NOT_FOUND);
        }
        if (existingProviderKey.getUserId() == null || !existingProviderKey.getUserId().equals(userId)) {
            throw new BusinessException("PROVIDER_KEY_FORBIDDEN", "Provider key does not belong to current user", HttpStatus.FORBIDDEN);
        }

        String encryptedKey = providerKeyCrypto.encrypt(rawProviderKey);
        existingProviderKey.setEncryptedKey(encryptedKey);
        existingProviderKey.setKeyHint(SensitiveDataMasker.maskSecret(rawProviderKey));
        existingProviderKey.setEnabled(true);
        providerKeyMapper.updateProviderKey(existingProviderKey);
        logger.info("Provider key updated, userId={}, providerId={}, providerKeyId={}, keyHint={}",
                userId, existingProviderKey.getProviderId(), existingProviderKey.getId(), existingProviderKey.getKeyHint());
        return toResponse(existingProviderKey);
    }

    private void validateProviderKey(Long providerId, String rawProviderKey) {
        if (providerId == null) {
            throw new BusinessException("PROVIDER_ID_REQUIRED", "Provider id is required");
        }
        if (rawProviderKey == null || rawProviderKey.isBlank()) {
            throw new BusinessException("PROVIDER_KEY_REQUIRED", "Provider key is required");
        }
    }

    private ProviderKeyResponse toResponse(ProviderKey providerKey) {
        ProviderKeyResponse response = new ProviderKeyResponse();
        response.setId(providerKey.getId());
        response.setProviderId(providerKey.getProviderId());
        response.setKeyHint(providerKey.getKeyHint());
        response.setEnabled(providerKey.getEnabled());
        return response;
    }
}
