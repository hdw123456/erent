package com.example.aigateway.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates refresh tokens and issues replacement access tokens. */
@Service
public class RefreshTokenService {

    private final Map<String, RefreshTokenInfo> store = new ConcurrentHashMap<>();

    public String create(String username) {
        String token = UUID.randomUUID().toString();

        store.put(
                token,
                new RefreshTokenInfo(
                        username,
                        Instant.now().plus(7, ChronoUnit.DAYS)));

        return token;
    }

    public String verifyAndGetUsername(String refreshToken) {
        RefreshTokenInfo info = store.get(refreshToken);

        if (info == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token 不存在");
        }

        if (info.expiresAt().isBefore(Instant.now())) {
            store.remove(refreshToken);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token 已过期");
        }

        return info.username();
    }

    private record RefreshTokenInfo(
            String username,
            Instant expiresAt) {
    }
}
