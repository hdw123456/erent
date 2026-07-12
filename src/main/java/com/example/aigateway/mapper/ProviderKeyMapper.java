package com.example.aigateway.mapper;

import com.example.aigateway.entity.ProviderKey;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for provider key data. */
public interface ProviderKeyMapper {
    void insertProviderKey(ProviderKey providerKey);
    void updateProviderKey(ProviderKey providerKey);
    ProviderKey getProviderKeyById(@Param("id") Long id);
    String getEncryptedProviderKeyByProviderId(@Param("providerId") Long providerId);
    String getEncryptedProviderKeyForCall(@Param("providerId") Long providerId, @Param("userId") Long userId);
    List<ProviderKey> listSchedulableProviderKeysForCall(
            @Param("providerId") Long providerId,
            @Param("userId") Long userId,
            @Param("limit") int limit);

    int markSuccess(@Param("id") Long id);

    int markFailure(
            @Param("id") Long id,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int markRateLimited(
            @Param("id") Long id,
            @Param("until") LocalDateTime until,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int markOverloaded(
            @Param("id") Long id,
            @Param("until") LocalDateTime until,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int markTempDisabled(
            @Param("id") Long id,
            @Param("until") LocalDateTime until,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int markError(
            @Param("id") Long id,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);
}
