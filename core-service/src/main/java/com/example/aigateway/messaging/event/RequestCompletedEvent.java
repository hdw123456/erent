package com.example.aigateway.messaging.event;

import java.time.Instant;

/** Immutable fact describing a completed provider request. */
public record RequestCompletedEvent(
        String eventId,
        String requestId,
        Long userId,
        Long apiKeyId,
        Long providerId,
        Long providerKeyId,
        Long modelId,
        int statusCode,
        int latencyMs,
        String errorCode,
        Instant occurredAt,
        int version,
        String traceId) {
}
