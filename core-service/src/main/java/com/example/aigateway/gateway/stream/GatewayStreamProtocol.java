package com.example.aigateway.gateway.stream;

/** Downstream wire protocols supported by the gateway's streaming endpoints. */
public enum GatewayStreamProtocol {
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC_MESSAGES,
    OPENAI_RESPONSES
}
