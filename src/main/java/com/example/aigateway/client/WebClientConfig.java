package com.example.aigateway.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Configures the Netty-backed WebClient used for upstream provider traffic. */
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient upstreamWebClient(
            WebClient.Builder builder,
            @Value("${gateway.upstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${gateway.upstream.read-timeout-ms:60000}") int readTimeoutMs,
            @Value("${gateway.upstream.response-timeout-ms:65000}") int responseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
