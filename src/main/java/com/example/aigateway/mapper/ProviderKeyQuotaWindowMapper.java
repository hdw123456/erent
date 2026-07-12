package com.example.aigateway.mapper;

import com.example.aigateway.entity.ProviderKeyQuotaWindow;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for provider key quota window data. */
public interface ProviderKeyQuotaWindowMapper {
    void upsertQuotaWindow(ProviderKeyQuotaWindow quotaWindow);

    ProviderKeyQuotaWindow getByProviderKeyIdAndWindowType(
            @Param("providerKeyId") Long providerKeyId,
            @Param("windowType") String windowType);

    List<ProviderKeyQuotaWindow> listByProviderKeyId(@Param("providerKeyId") Long providerKeyId);

    int incrementQuotaUsed(
            @Param("providerKeyId") Long providerKeyId,
            @Param("windowType") String windowType,
            @Param("amount") BigDecimal amount);
}
