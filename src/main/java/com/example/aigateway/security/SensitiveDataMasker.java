package com.example.aigateway.security;

/** Produces safe hints for secrets without exposing raw values. */
public final class SensitiveDataMasker {
    private SensitiveDataMasker() {
    }

    public static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
