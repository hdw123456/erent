package com.example.aigateway.common;

import java.time.OffsetDateTime;

/** Stable error envelope returned by HTTP and streaming endpoints. */
public record ErrorResponse(
        String code,
        String message,
        Object details,
        OffsetDateTime timestamp
) {
    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(code, message, details, OffsetDateTime.now());
    }
}
