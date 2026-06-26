package com.example.aigateway.mapper;

import com.example.aigateway.dto.ProviderModelPricing;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ModelMapper {
    List<ProviderModelPricing> getAvailableModelsAndPricingByProviderCode(
            @Param("providerCode") String providerCode
    );
}
