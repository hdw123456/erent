package com.example.aigateway.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** Retains upstream status, headers, and body for availability decisions. */
public class ProviderUpstreamException extends BusinessException {
    private final int upstreamStatus;
    private final HttpHeaders headers;
    private final String responseBody;

    public ProviderUpstreamException(
            String code,
            String message,
            HttpStatus status,
            int upstreamStatus,
            HttpHeaders headers,
            String responseBody) {
        super(code, message, status);
        this.upstreamStatus = upstreamStatus;
        this.headers = headers == null ? HttpHeaders.EMPTY : headers;
        this.responseBody = responseBody;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
