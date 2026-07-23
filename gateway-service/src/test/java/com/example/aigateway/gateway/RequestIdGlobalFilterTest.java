package com.example.aigateway.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdGlobalFilterTest {
    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();

    @Test
    void preservesValidRequestIdAndRemovesInternalHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/health")
                        .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "request-123")
                        .header("X-Internal-Token", "forged")
                        .header("X-User-Id", "99")
                        .build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = filtered -> {
            captured.set(filtered);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(
                "request-123",
                captured.get().getRequest().getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER));
        assertFalse(captured.get().getRequest().getHeaders().containsKey("X-Internal-Token"));
        assertFalse(captured.get().getRequest().getHeaders().containsKey("X-User-Id"));
        assertEquals(
                "request-123",
                captured.get().getResponse().getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER));
    }

    @Test
    void createsRequestIdWhenHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/health").build());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            captured.set(filtered);
            return Mono.empty();
        }).block();

        assertNotNull(captured.get().getRequest().getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER));
    }
}
