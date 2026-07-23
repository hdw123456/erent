package com.example.aigateway.provider;

import com.example.aigateway.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves a provider code to its registered adapter. */
@Component
public class ProviderAdapterFactory {
    private final Map<String, ProviderAdapter> adapters;

    public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
        this.adapters = new HashMap<>();
        for (ProviderAdapter adapter : adapters) {
            for (String providerCode : adapter.providerCodes()) {
                this.adapters.put(normalize(providerCode), adapter);
            }
        }
    }

    public ProviderAdapter getAdapter(String providerCode) {
        ProviderAdapter adapter = adapters.get(normalize(providerCode));
        if (adapter == null) {
            throw new BusinessException(
                    "PROVIDER_ADAPTER_NOT_FOUND",
                    "Provider adapter not found: " + providerCode,
                    HttpStatus.BAD_REQUEST
            );
        }
        return adapter;
    }

    private String normalize(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BusinessException("PROVIDER_REQUIRED", "Provider is required");
        }
        return providerCode.trim().toUpperCase(Locale.ROOT);
    }
}
