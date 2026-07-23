package com.example.aigateway.gateway;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.discovery.register-enabled=false"
        })
class GatewayRoutingIntegrationTest {
    private static final DisposableServer UPSTREAM = startUpstream();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void registerCoreService(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances.core-service[0].uri",
                () -> "http://127.0.0.1:" + UPSTREAM.port());
    }

    private static DisposableServer startUpstream() {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> response
                                .status(200)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "upstream-request-id")
                                .sendString(Mono.just("""
                                        {
                                          "authorization": "%s",
                                          "idempotencyKey": "%s",
                                          "requestId": "%s",
                                          "body": %s
                                        }
                                        """.formatted(
                                                request.requestHeaders().get("Authorization"),
                                                request.requestHeaders().get("Idempotency-Key"),
                                                request.requestHeaders().get(RequestIdGlobalFilter.REQUEST_ID_HEADER),
                                                body)))
                                .then()))
                .bindNow();
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.disposeNow();
    }

    @Test
    void routesVersionedApiThroughLoadBalancerAndPreservesPublicContract() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gatewayPort)
                .build();

        client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer platform-key")
                .header("Idempotency-Key", "idem-001")
                .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "request-001")
                .header("X-Internal-Token", "forged")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", "demo-model"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestIdGlobalFilter.REQUEST_ID_HEADER, "request-001")
                .expectBody()
                .jsonPath("$.authorization").isEqualTo("Bearer platform-key")
                .jsonPath("$.idempotencyKey").isEqualTo("idem-001")
                .jsonPath("$.requestId").isEqualTo("request-001")
                .jsonPath("$.body.model").isEqualTo("demo-model");
    }

}
