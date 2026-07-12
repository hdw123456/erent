package com.example.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated request data for update api key operations. */
public class UpdateApiKeyRequest {
    @NotBlank
    @Size(max = 64)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
