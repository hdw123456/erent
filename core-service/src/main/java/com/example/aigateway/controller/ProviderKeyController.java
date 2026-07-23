package com.example.aigateway.controller;

import com.example.aigateway.dto.request.CreateProviderKeyRequest;
import com.example.aigateway.dto.request.UpdateProviderKeyRequest;
import com.example.aigateway.dto.response.ProviderKeyResponse;
import com.example.aigateway.service.CurrentUserService;
import com.example.aigateway.service.ProviderKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manages encrypted upstream provider credentials. */
@RestController
@RequestMapping("/api/provider-keys")
public class ProviderKeyController {
    private final ProviderKeyService providerKeyService;
    private final CurrentUserService currentUserService;

    public ProviderKeyController(ProviderKeyService providerKeyService, CurrentUserService currentUserService) {
        this.providerKeyService = providerKeyService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ProviderKeyResponse saveProviderKey(
            @Valid @RequestBody CreateProviderKeyRequest request
    ) {
        return providerKeyService.saveProviderKey(request, currentUserService.getCurrentUserId());
    }

    @PatchMapping("/{id}")
    public ProviderKeyResponse updateProviderKey(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProviderKeyRequest request
    ) {
        return providerKeyService.updateProviderKey(id, request, currentUserService.getCurrentUserId());
    }
}
