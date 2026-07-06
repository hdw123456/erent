package com.example.aigateway.controller;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.gateway.GatewayResponseAdapter;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.example.aigateway.service.ModelService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayModelController {
    private final ModelService modelService;
    private final GatewayResponseAdapter gatewayResponseAdapter;

    public GatewayModelController(ModelService modelService, GatewayResponseAdapter gatewayResponseAdapter) {
        this.modelService = modelService;
        this.gatewayResponseAdapter = gatewayResponseAdapter;
    }

    @GetMapping("/v1/models")
    public Map<String, Object> models(
            @RequestParam(required = false) String providerCode,
            Authentication authentication
    ) {
        currentApiKey(authentication);
        List<ProviderModelPricing> models = modelService.listAvailableModels(providerCode);
        return gatewayResponseAdapter.toModels(models);
    }

    private ApiKeyPrincipal currentApiKey(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new BusinessException("UNAUTHORIZED", "API key is required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
