package com.example.aigateway.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Creates one bounded request identifier and removes spoofable internal headers. */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_USER_HEADER = "X-User-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(INTERNAL_TOKEN_HEADER);
                    headers.remove(INTERNAL_USER_HEADER);
                    headers.set(REQUEST_ID_HEADER, requestId);
                })
                .build();

        ServerWebExchange filteredExchange = exchange.mutate().request(request).build();
        filteredExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        filteredExchange.getResponse().beforeCommit(() -> {
            filteredExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(filteredExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String resolveRequestId(String candidate) {
        if (candidate == null
                || candidate.isBlank()
                || candidate.length() > MAX_REQUEST_ID_LENGTH
                || !SAFE_REQUEST_ID.matcher(candidate.trim()).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate.trim();
    }
}
