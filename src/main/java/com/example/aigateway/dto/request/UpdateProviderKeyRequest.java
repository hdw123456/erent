package com.example.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProviderKeyRequest {
    @NotBlank
    @Size(max = 512)
    private String rawProviderKey;

    public String getRawProviderKey() {
        return rawProviderKey;
    }

    public void setRawProviderKey(String rawProviderKey) {
        this.rawProviderKey = rawProviderKey;
    }
}
