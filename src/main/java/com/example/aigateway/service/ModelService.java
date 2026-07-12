package com.example.aigateway.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.ModelMapper;

/** Resolves enabled models together with their active pricing rules. */
@Service
public class ModelService {
    private final ModelMapper modelMapper;

    public ModelService(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public List<ProviderModelPricing> listAvailableModels(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getAllAvailableModelsAndPricing();
        }
        return getAvailableModelsAndPricingByProviderCode(providerCode);
    }

    public List<ProviderModelPricing> getAvailableModelsAndPricingByProviderCode(String providerCode) {
        return modelMapper.getAvailableModelsAndPricingByProviderCode(providerCode);
    }

    public List<ProviderModelPricing> getAllAvailableModelsAndPricing() {
        return modelMapper.getAllAvailableModelsAndPricing();
    }

    public ProviderModelPricing getAvailableModelByCode(String providerCode, String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException("MODEL_REQUIRED", "Model is required");
        }
        ProviderModelPricing model = modelMapper.getAvailableModelByCode(normalize(providerCode), modelCode);
        if (model == null) {
            throw new BusinessException("MODEL_NOT_FOUND", "Model is not available", HttpStatus.NOT_FOUND);
        }
        return model;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
