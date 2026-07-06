package com.example.aigateway.service;

import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.exception.ProviderUpstreamException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class UpstreamErrorService {
    public BusinessException toBusinessException(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException;
        }
        if (throwable instanceof TimeoutException) {
            return new BusinessException("PROVIDER_TIMEOUT", "Provider request timed out", HttpStatus.GATEWAY_TIMEOUT);
        }
        if (throwable instanceof WebClientRequestException) {
            return new BusinessException("PROVIDER_UNAVAILABLE", "Provider request failed", HttpStatus.BAD_GATEWAY);
        }
        if (throwable instanceof WebClientResponseException responseException) {
            return toProviderResponseException(
                    responseException.getStatusCode().value(),
                    responseException.getHeaders(),
                    responseException.getResponseBodyAsString());
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            return toBusinessException(throwable.getCause());
        }
        return new BusinessException("PROVIDER_UPSTREAM_ERROR", "Provider request failed", HttpStatus.BAD_GATEWAY);
    }

    public ProviderUpstreamException toProviderResponseException(
            int statusCode,
            org.springframework.http.HttpHeaders headers,
            String responseBody) {
        return switch (statusCode) {
            case 429 -> new ProviderUpstreamException(
                    "PROVIDER_RATE_LIMITED",
                    "Provider rate limit exceeded",
                    HttpStatus.TOO_MANY_REQUESTS,
                    statusCode,
                    headers,
                    responseBody
            );
            case 401, 403 -> new ProviderUpstreamException(
                    "PROVIDER_AUTH_FAILED",
                    "Provider authentication failed",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    headers,
                    responseBody
            );
            case 404 -> new ProviderUpstreamException(
                    "PROVIDER_MODEL_NOT_FOUND",
                    "Provider model not found",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    headers,
                    responseBody
            );
            default -> new ProviderUpstreamException(
                    "PROVIDER_UPSTREAM_ERROR",
                    "Provider request failed",
                    HttpStatus.BAD_GATEWAY,
                    statusCode,
                    headers,
                    responseBody
            );
        };
    }
}
