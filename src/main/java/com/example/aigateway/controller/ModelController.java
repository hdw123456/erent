package com.example.aigateway.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.service.ModelService;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public List<ProviderModelPricing> listModels(@RequestParam(required = false) String providerCode) {
        return modelService.listAvailableModels(providerCode);
    }
}
