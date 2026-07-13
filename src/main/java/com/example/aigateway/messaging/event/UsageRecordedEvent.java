package com.example.aigateway.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable fact describing usage that has already been recorded by the core transaction. */
public record UsageRecordedEvent(
        String eventId,
        String requestId,
        Long userId,
        Long modelId,
        Long providerKeyId,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal costAmount,
        String usageSource,
        Instant occurredAt,
        int version,
        String traceId) {
}
