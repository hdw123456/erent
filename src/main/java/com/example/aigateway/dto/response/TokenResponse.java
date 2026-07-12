package com.example.aigateway.dto.response;

/** Serialized response data for token operations. */
public record TokenResponse(
        String accessToken,
        String refreshToken) {
}
