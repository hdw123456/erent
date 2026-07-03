package com.example.aigateway.mapper;

import com.example.aigateway.entity.ApiKey;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ApiKeyMapper {

    void insertApiKey(@Param("apiKey") ApiKey apiKey, @Param("userId") long userId);
    List<ApiKey> getUserApi(@Param("userId") long userId);
    ApiKey getApiKeyByName(@Param("apiName") String apiName);
    ApiKey getApiKeyById(@Param("apiKeyId") long apiKeyId);
    ApiKey getApiKeyByKeyHash(@Param("keyHash") String keyHash);
    void updateApiKey(ApiKey apiKey);
    void updateLastUsedAt(@Param("id") long id);
    void deleteApiKey(ApiKey apiKey);
    
}
