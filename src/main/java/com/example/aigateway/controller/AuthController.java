package com.example.aigateway.controller;

import com.example.aigateway.dto.request.LoginRequest;
import com.example.aigateway.dto.request.UserRegister;
import com.example.aigateway.dto.response.TokenResponse;
import com.example.aigateway.service.JwtService;
import com.example.aigateway.service.RefreshTokenService;
import com.example.aigateway.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
            JwtService jwtService, RefreshTokenService refreshTokenService,
            UserDetailsService userDetailsService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    public void registerUser(@Valid @RequestBody UserRegister request) {
        userService.register(request.getUsername(), request.getPassword(), request.getEmail());

    }

    @PostMapping("/login")
    public TokenResponse loginUser(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        String accessToken = jwtService.generateAccessToken(authentication);
        String refreshToken = refreshTokenService.create(authentication.getName());

        return new TokenResponse(accessToken, refreshToken);
    }

    @PostMapping("/refresh")
    public TokenResponse refreshToken(@RequestHeader("Authorization") String refreshToken) {
        if (refreshToken == null || !refreshToken.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid refresh token");
        }

        String token = refreshToken.substring(7);
        String username = refreshTokenService.verifyAndGetUsername(token);

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken = jwtService.generateAccessToken(userDetails);

        return new TokenResponse(accessToken, token);
    }

}
