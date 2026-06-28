package com.example.aigateway.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.mapper.ModelMapper;

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
}
