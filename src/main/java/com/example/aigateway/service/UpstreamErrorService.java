package com.example.aigateway.service;

import com.example.aigateway.exception.BusinessException;
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
            return toProviderResponseException(responseException.getStatusCode().value());
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            return toBusinessException(throwable.getCause());
        }
        return new BusinessException("PROVIDER_UPSTREAM_ERROR", "Provider request failed", HttpStatus.BAD_GATEWAY);
    }

    private BusinessException toProviderResponseException(int statusCode) {
        return switch (statusCode) {
            case 429 -> new BusinessException(
                    "PROVIDER_RATE_LIMITED",
                    "Provider rate limit exceeded",
                    HttpStatus.TOO_MANY_REQUESTS
            );
            case 401, 403 -> new BusinessException(
                    "PROVIDER_AUTH_FAILED",
                    "Provider authentication failed",
                    HttpStatus.BAD_GATEWAY
            );
            case 404 -> new BusinessException(
                    "PROVIDER_MODEL_NOT_FOUND",
                    "Provider model not found",
                    HttpStatus.BAD_GATEWAY
            );
            default -> new BusinessException(
                    "PROVIDER_UPSTREAM_ERROR",
                    "Provider request failed",
                    HttpStatus.BAD_GATEWAY
            );
        };
    }
}
