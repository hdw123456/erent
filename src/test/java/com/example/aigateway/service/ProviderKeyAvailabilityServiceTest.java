package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.exception.ProviderUpstreamException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ProviderKeyAvailabilityServiceTest {

    @Test
    void rateLimitedProviderKeyShouldBeCooledDownFromRetryAfterHeader() {
        ProviderKeyMapper providerKeyMapper = mock(ProviderKeyMapper.class);
        ProviderKeyAvailabilityService service = new ProviderKeyAvailabilityService(providerKeyMapper);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "60");
        ProviderUpstreamException exception = new ProviderUpstreamException(
                "PROVIDER_RATE_LIMITED",
                "Provider rate limit exceeded",
                HttpStatus.TOO_MANY_REQUESTS,
                429,
                headers,
                "");

        service.markFailure(10L, exception, exception);

        ArgumentCaptor<LocalDateTime> untilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(providerKeyMapper).markRateLimited(
                eq(10L),
                untilCaptor.capture(),
                eq("PROVIDER_RATE_LIMITED"),
                eq("Provider rate limit exceeded"));
        assertTrue(untilCaptor.getValue().isAfter(LocalDateTime.now().plusSeconds(30)));
    }

    @Test
    void authFailureShouldMarkProviderKeyAsError() {
        ProviderKeyMapper providerKeyMapper = mock(ProviderKeyMapper.class);
        ProviderKeyAvailabilityService service = new ProviderKeyAvailabilityService(providerKeyMapper);
        BusinessException exception = new BusinessException(
                "PROVIDER_AUTH_FAILED",
                "Provider authentication failed",
                HttpStatus.BAD_GATEWAY);

        service.markFailure(20L, exception, exception);

        verify(providerKeyMapper).markError(
                20L,
                "PROVIDER_AUTH_FAILED",
                "Provider authentication failed");
    }
}
