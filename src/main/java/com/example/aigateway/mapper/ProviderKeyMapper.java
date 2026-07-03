package com.example.aigateway.mapper;

import com.example.aigateway.entity.ProviderKey;
import org.apache.ibatis.annotations.Param;

public interface ProviderKeyMapper {
    void insertProviderKey(ProviderKey providerKey);
    void updateProviderKey(ProviderKey providerKey);
    ProviderKey getProviderKeyById(@Param("id") Long id);
    String getEncryptedProviderKeyByProviderId(@Param("providerId") Long providerId);
    String getEncryptedProviderKeyForCall(@Param("providerId") Long providerId, @Param("userId") Long userId);
}
