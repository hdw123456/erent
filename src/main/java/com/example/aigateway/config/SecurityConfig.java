package com.example.aigateway.config;

import java.time.OffsetDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, exception) -> writeErrorResponse(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication is required"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeErrorResponse(
                                response,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "Access is denied"
                        ))
                )
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeErrorResponse(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"%s","message":"%s","details":null,"timestamp":"%s"}""".formatted(
                code,
                message,
                OffsetDateTime.now()
        ));
    }
}
