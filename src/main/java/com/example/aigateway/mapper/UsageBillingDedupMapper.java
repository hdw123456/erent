package com.example.aigateway.mapper;

import com.example.aigateway.entity.UsageBillingDedup;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for usage billing dedup data. */
public interface UsageBillingDedupMapper {
    int insertUsageBillingDedup(UsageBillingDedup usageBillingDedup);

    int insertUsageBillingDedupIgnore(UsageBillingDedup usageBillingDedup);

    UsageBillingDedup getByRequestIdAndApiKeyId(
            @Param("requestId") String requestId,
            @Param("apiKeyId") Long apiKeyId
    );

    UsageBillingDedup getByRequestIdAndApiKeyIdForUpdate(
            @Param("requestId") String requestId,
            @Param("apiKeyId") Long apiKeyId
    );
}
