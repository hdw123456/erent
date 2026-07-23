package com.example.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;


/** Validated request data for login operations. */
public record LoginRequest(
        @NotBlank
        String username,
        @NotBlank
        String password) {
}
