package com.example.aigateway.service;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.entity.ProviderKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.security.ProviderKeyCrypto;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Builds call-scoped credentials from encrypted keys or environment fallback. */
@Service
public class ProviderCredentialService {
    private final ProviderKeyMapper providerKeyMapper;
    private final ProviderKeyCrypto providerKeyCrypto;
    private final String openaiBaseUrl;
    private final String openrouterBaseUrl;
    private final String openrouterApiKey;
    private final String openrouterHttpReferer;
    private final String openrouterAppTitle;

    public ProviderCredentialService(
            ProviderKeyMapper providerKeyMapper,
            ProviderKeyCrypto providerKeyCrypto,
            @Value("${providers.openai.base-url:https://api.openai.com/v1}") String openaiBaseUrl,
            @Value("${providers.openrouter.base-url:https://openrouter.ai/api/v1}") String openrouterBaseUrl,
            @Value("${providers.openrouter.api-key:}") String openrouterApiKey,
            @Value("${providers.openrouter.http-referer:}") String openrouterHttpReferer,
            @Value("${providers.openrouter.app-title:AI Gateway Learning}") String openrouterAppTitle
    ) {
        this.providerKeyMapper = providerKeyMapper;
        this.providerKeyCrypto = providerKeyCrypto;
        this.openaiBaseUrl = openaiBaseUrl;
        this.openrouterBaseUrl = openrouterBaseUrl;
        this.openrouterApiKey = openrouterApiKey;
        this.openrouterHttpReferer = openrouterHttpReferer;
        this.openrouterAppTitle = openrouterAppTitle;
    }

    public ProviderCredential fromRawKey(Long providerId, String providerCode, String rawProviderKey, String baseUrl) {
        return new ProviderCredential(providerId, normalize(providerCode), rawProviderKey, baseUrl);
    }

    public ProviderCredential resolveForCall(ProviderModelPricing model, Long userId) {
        String providerCode = normalize(model.getProviderCode());
        ProviderKey providerKey = providerKeyMapper
                .listSchedulableProviderKeysForCall(model.getProviderId(), userId, 1)
                .stream()
                .findFirst()
                .orElse(null);
        if (providerKey != null) {
            return toCredential(model, providerKey);
        }
        return resolveEnvironmentCredential(model);
    }

    public ProviderCredential toCredential(ProviderModelPricing model, ProviderKey providerKey) {
        if (providerKey == null) {
            throw new BusinessException(
                    "PROVIDER_KEY_NOT_CONFIGURED",
                    "Provider key is not configured",
                    HttpStatus.BAD_GATEWAY
            );
        }
        String providerCode = normalize(model.getProviderCode());
        String rawProviderKey = decryptProviderKey(providerKey.getEncryptedKey());
        ProviderCredential credential = new ProviderCredential(
                providerKey.getId(),
                providerKey.getProviderKeyType(),
                model.getProviderId(),
                providerCode,
                rawProviderKey,
                resolveProviderKeyBaseUrl(providerCode, providerKey)
        );
        credential.setHeaders(resolveHeaders(providerCode));
        return credential;
    }

    public ProviderCredential resolveEnvironmentCredential(ProviderModelPricing model) {
        String providerCode = normalize(model.getProviderCode());
        if (supportsEnvironmentCredential(providerCode)) {
            ProviderCredential credential = new ProviderCredential(
                    null,
                    "ENVIRONMENT",
                    model.getProviderId(),
                    providerCode,
                    openrouterApiKey,
                    resolveBaseUrl(providerCode)
            );
            credential.setHeaders(resolveHeaders(providerCode));
            return credential;
        }

        throw new BusinessException(
                "PROVIDER_KEY_NOT_CONFIGURED",
                "Provider key is not configured",
                HttpStatus.BAD_GATEWAY
        );
    }

    public boolean supportsEnvironmentCredential(ProviderModelPricing model) {
        return model != null && supportsEnvironmentCredential(normalize(model.getProviderCode()));
    }

    private boolean supportsEnvironmentCredential(String providerCode) {
        return "OPENROUTER".equals(providerCode) && openrouterApiKey != null && !openrouterApiKey.isBlank();
    }

    private String decryptProviderKey(String encryptedKey) {
        if (encryptedKey != null && !encryptedKey.isBlank()) {
            try {
                return providerKeyCrypto.decrypt(encryptedKey);
            } catch (RuntimeException exception) {
                throw new BusinessException(
                        "PROVIDER_KEY_INVALID",
                        "Provider key cannot be decrypted",
                        HttpStatus.BAD_GATEWAY
                );
            }
        }

        throw new BusinessException(
                "PROVIDER_KEY_NOT_CONFIGURED",
                "Provider key is not configured",
                HttpStatus.BAD_GATEWAY
                );
    }

    private String resolveProviderKeyBaseUrl(String providerCode, ProviderKey providerKey) {
        if (providerKey.getBaseUrl() != null && !providerKey.getBaseUrl().isBlank()) {
            return providerKey.getBaseUrl().trim();
        }
        return resolveBaseUrl(providerCode);
    }

    private String resolveBaseUrl(String providerCode) {
        return switch (providerCode) {
            case "OPENAI" -> openaiBaseUrl;
            case "OPENROUTER" -> openrouterBaseUrl;
            default -> throw new BusinessException(
                    "PROVIDER_BASE_URL_NOT_CONFIGURED",
                    "Provider base URL is not configured",
                    HttpStatus.BAD_GATEWAY
            );
        };
    }

    private Map<String, String> resolveHeaders(String providerCode) {
        Map<String, String> headers = new LinkedHashMap<>();
        if ("OPENROUTER".equals(providerCode)) {
            if (openrouterHttpReferer != null && !openrouterHttpReferer.isBlank()) {
                headers.put("HTTP-Referer", openrouterHttpReferer);
            }
            if (openrouterAppTitle != null && !openrouterAppTitle.isBlank()) {
                headers.put("X-OpenRouter-Title", openrouterAppTitle);
            }
        }
        return headers;
    }

    private String normalize(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BusinessException("PROVIDER_REQUIRED", "Provider is required");
        }
        return providerCode.trim().toUpperCase(Locale.ROOT);
    }
}
