package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.aigateway.exception.BusinessException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;

class UpstreamErrorServiceTest {
    private final UpstreamErrorService upstreamErrorService = new UpstreamErrorService();

    @Test
    void timeoutShouldReturnUnifiedProviderTimeoutError() {
        BusinessException exception = upstreamErrorService.toBusinessException(new TimeoutException());

        assertEquals("PROVIDER_TIMEOUT", exception.getCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
        assertEquals("Provider request timed out", exception.getMessage());
    }

    @Test
    void requestFailureShouldReturnUnifiedProviderUnavailableError() {
        BusinessException exception = upstreamErrorService.toBusinessException(
                new WebClientRequestException(
                        new RuntimeException("connect failed"),
                        HttpMethod.POST,
                        URI.create("https://provider.example.test/v1/chat/completions"),
                        HttpHeaders.EMPTY
                )
        );

        assertEquals("PROVIDER_UNAVAILABLE", exception.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("Provider request failed", exception.getMessage());
    }
}
