package com.example.aigateway.client;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UpstreamHttpClient {
    private final WebClient webClient;
    private final Duration requestTimeout;

    public UpstreamHttpClient(
            @Qualifier("upstreamWebClient") WebClient webClient,
            @Value("${gateway.upstream.request-timeout-ms:70000}") long requestTimeoutMs
    ) {
        this.webClient = webClient;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    public <T> Mono<T> postJson(String url, Object body, Map<String, String> headers, Class<T> responseType) {
        return webClient.post()
                .uri(url)
                .headers(httpHeaders -> httpHeaders.setAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(requestTimeout);
    }

    public Flux<String> postJsonStream(String url, Object body, Map<String, String> headers) {
        return webClient.post()
                .uri(url)
                .headers(httpHeaders -> httpHeaders.setAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !data.isBlank());
    }
}
