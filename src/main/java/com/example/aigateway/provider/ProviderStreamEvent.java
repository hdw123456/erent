package com.example.aigateway.provider;

import com.example.aigateway.dto.response.ChatResponse;

/**
 * Provider-neutral representation of one upstream streaming event.
 *
 * <p>The raw payload is retained for lossless OpenAI-compatible passthrough,
 * while normalized fields support usage aggregation and protocol conversion.</p>
 */
public record ProviderStreamEvent(
        String rawData,
        String responseId,
        String model,
        String role,
        String textDelta,
        String finishReason,
        ChatResponse.Usage usage,
        boolean done) {

    public static ProviderStreamEvent done(String rawData) {
        return new ProviderStreamEvent(rawData, null, null, null, null, null, null, true);
    }
}
