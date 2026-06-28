package com.example.aigateway.controller;

import com.example.aigateway.dto.request.CreateProviderKeyRequest;
import com.example.aigateway.dto.request.UpdateProviderKeyRequest;
import com.example.aigateway.dto.response.ProviderKeyResponse;
import com.example.aigateway.service.ProviderKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider-keys")
public class ProviderKeyController {
    private final ProviderKeyService providerKeyService;

    public ProviderKeyController(ProviderKeyService providerKeyService) {
        this.providerKeyService = providerKeyService;
    }

    @PostMapping
    public ProviderKeyResponse saveProviderKey(
            @Valid @RequestBody CreateProviderKeyRequest request,
            @RequestHeader("X-User-Id") long userId
    ) {
        return providerKeyService.saveProviderKey(request.getProviderId(), request.getRawProviderKey(), userId);
    }

    @PatchMapping("/{id}")
    public ProviderKeyResponse updateProviderKey(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProviderKeyRequest request,
            @RequestHeader("X-User-Id") long userId
    ) {
        return providerKeyService.updateProviderKey(id, request.getRawProviderKey(), userId);
    }
}
