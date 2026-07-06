package com.example.aigateway.mapper;

import com.example.aigateway.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Param;

public interface IdempotencyRecordMapper {
    int insertIdempotencyRecord(IdempotencyRecord idempotencyRecord);

    int insertIdempotencyRecordIgnore(IdempotencyRecord idempotencyRecord);

    IdempotencyRecord getByApiKeyIdAndIdempotencyKey(
            @Param("apiKeyId") Long apiKeyId,
            @Param("idempotencyKey") String idempotencyKey
    );

    IdempotencyRecord getByApiKeyIdAndIdempotencyKeyForUpdate(
            @Param("apiKeyId") Long apiKeyId,
            @Param("idempotencyKey") String idempotencyKey
    );

    IdempotencyRecord getByRequestId(@Param("requestId") String requestId);

    int updateIdempotencyRecordResult(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("responseJson") String responseJson,
            @Param("errorCode") String errorCode
    );
}
