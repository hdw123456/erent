package com.example.aigateway.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken) {
}
