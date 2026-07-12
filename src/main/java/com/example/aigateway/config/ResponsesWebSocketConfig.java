package com.example.aigateway.config;

import com.example.aigateway.gateway.websocket.ResponsesWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the persistent Responses API WebSocket transport. */
@Configuration
@EnableWebSocket
public class ResponsesWebSocketConfig implements WebSocketConfigurer {
    private final ResponsesWebSocketHandler responsesWebSocketHandler;

    public ResponsesWebSocketConfig(ResponsesWebSocketHandler responsesWebSocketHandler) {
        this.responsesWebSocketHandler = responsesWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                responsesWebSocketHandler,
                "/v1/responses",
                "/responses",
                "/backend-api/codex/responses");
    }
}
