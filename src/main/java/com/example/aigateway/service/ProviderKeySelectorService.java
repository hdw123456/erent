package com.example.aigateway.service;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.entity.ProviderKey;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import com.example.aigateway.provider.ProviderCredential;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Selects ordered, schedulable provider credentials for a call. */
@Service
public class ProviderKeySelectorService {
    private final ProviderKeyMapper providerKeyMapper;
    private final ProviderCredentialService providerCredentialService;
    private final int maxFailoverAttempts;

    public ProviderKeySelectorService(
            ProviderKeyMapper providerKeyMapper,
            ProviderCredentialService providerCredentialService,
            @Value("${gateway.provider-key.max-failover-attempts:3}") int maxFailoverAttempts) {
        this.providerKeyMapper = providerKeyMapper;
        this.providerCredentialService = providerCredentialService;
        this.maxFailoverAttempts = Math.max(1, maxFailoverAttempts);
    }

    public List<ProviderCredential> selectCredentials(ProviderModelPricing model, Long userId) {
        List<ProviderKey> providerKeys = providerKeyMapper.listSchedulableProviderKeysForCall(
                model.getProviderId(), userId, maxFailoverAttempts);
        List<ProviderCredential> credentials = new ArrayList<>();
        for (ProviderKey providerKey : providerKeys) {
            credentials.add(providerCredentialService.toCredential(model, providerKey));
        }
        if (credentials.isEmpty() && providerCredentialService.supportsEnvironmentCredential(model)) {
            credentials.add(providerCredentialService.resolveEnvironmentCredential(model));
        }
        if (credentials.isEmpty()) {
            throw new BusinessException(
                    "PROVIDER_KEY_NOT_CONFIGURED",
                    "No schedulable provider key is available",
                    HttpStatus.BAD_GATEWAY);
        }
        return credentials;
    }
}
