package com.example.aigateway.gateway.stream;

/** One downstream SSE frame; {@code event} is null for data-only protocols. */
public record GatewayStreamFrame(String event, String data) {
    public static GatewayStreamFrame data(String data) {
        return new GatewayStreamFrame(null, data);
    }
}
