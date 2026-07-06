package com.example.aigateway.service;

import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.exception.ProviderUpstreamException;
import com.example.aigateway.mapper.ProviderKeyMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class ProviderKeyAvailabilityService {
    private static final Logger logger = LoggerFactory.getLogger(ProviderKeyAvailabilityService.class);
    private static final Duration RATE_LIMIT_FALLBACK_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration TEMP_DISABLED_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration OVERLOADED_COOLDOWN = Duration.ofMinutes(2);
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private static final List<String> RESET_HEADERS = List.of(
            "retry-after",
            "x-ratelimit-reset",
            "x-ratelimit-reset-requests",
            "x-ratelimit-reset-tokens",
            "anthropic-ratelimit-unified-reset",
            "anthropic-ratelimit-unified-5h-reset",
            "anthropic-ratelimit-unified-7d-reset"
    );

    private final ProviderKeyMapper providerKeyMapper;

    public ProviderKeyAvailabilityService(ProviderKeyMapper providerKeyMapper) {
        this.providerKeyMapper = providerKeyMapper;
    }

    public void markSuccess(Long providerKeyId) {
        if (providerKeyId == null) {
            return;
        }
        runStateUpdate(providerKeyId, () -> providerKeyMapper.markSuccess(providerKeyId));
    }

    public void markFailure(Long providerKeyId, BusinessException exception, Throwable throwable) {
        if (providerKeyId == null || exception == null) {
            return;
        }
        String code = exception.getCode();
        String message = truncate(exception.getMessage(), 255);
        ProviderUpstreamException upstreamException = findProviderUpstreamException(throwable).orElse(null);

        if ("PROVIDER_AUTH_FAILED".equals(code)) {
            runStateUpdate(providerKeyId, () -> providerKeyMapper.markError(providerKeyId, code, message));
            return;
        }
        if ("PROVIDER_RATE_LIMITED".equals(code)) {
            LocalDateTime resetAt = resolveRateLimitResetAt(upstreamException)
                    .orElse(LocalDateTime.now().plus(RATE_LIMIT_FALLBACK_COOLDOWN));
            runStateUpdate(providerKeyId, () -> providerKeyMapper.markRateLimited(providerKeyId, resetAt, code, message));
            return;
        }
        if ("PROVIDER_TIMEOUT".equals(code) || "PROVIDER_UNAVAILABLE".equals(code)) {
            LocalDateTime until = LocalDateTime.now().plus(TEMP_DISABLED_COOLDOWN);
            runStateUpdate(providerKeyId, () -> providerKeyMapper.markTempDisabled(providerKeyId, until, code, message));
            return;
        }
        if ("PROVIDER_UPSTREAM_ERROR".equals(code) && isOverload(upstreamException)) {
            LocalDateTime until = LocalDateTime.now().plus(OVERLOADED_COOLDOWN);
            runStateUpdate(providerKeyId, () -> providerKeyMapper.markOverloaded(providerKeyId, until, code, message));
            return;
        }

        runStateUpdate(providerKeyId, () -> providerKeyMapper.markFailure(providerKeyId, code, message));
    }

    private boolean isOverload(ProviderUpstreamException upstreamException) {
        if (upstreamException == null) {
            return false;
        }
        int status = upstreamException.getUpstreamStatus();
        return status == 502 || status == 503 || status == 504 || status == 529;
    }

    private Optional<LocalDateTime> resolveRateLimitResetAt(ProviderUpstreamException upstreamException) {
        if (upstreamException == null) {
            return Optional.empty();
        }
        HttpHeaders headers = upstreamException.getHeaders();
        LocalDateTime selected = null;
        for (String headerName : RESET_HEADERS) {
            String headerValue = headers.getFirst(headerName);
            Optional<LocalDateTime> parsed = parseResetValue(headerName, headerValue);
            if (parsed.isPresent() && parsed.get().isAfter(LocalDateTime.now())) {
                if (selected == null || parsed.get().isAfter(selected)) {
                    selected = parsed.get();
                }
            }
        }
        return Optional.ofNullable(selected);
    }

    private Optional<LocalDateTime> parseResetValue(String headerName, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if ("retry-after".equalsIgnoreCase(headerName)) {
            Optional<LocalDateTime> retryAfter = parseRetryAfter(trimmed);
            if (retryAfter.isPresent()) {
                return retryAfter;
            }
        }
        Optional<LocalDateTime> duration = parseDurationOffset(trimmed);
        if (duration.isPresent()) {
            return duration;
        }
        Optional<LocalDateTime> epoch = parseEpoch(trimmed);
        if (epoch.isPresent()) {
            return epoch;
        }
        return parseDateTime(trimmed);
    }

    private Optional<LocalDateTime> parseRetryAfter(String value) {
        if (value.matches("\\d+")) {
            return Optional.of(LocalDateTime.now().plusSeconds(Long.parseLong(value)));
        }
        try {
            return Optional.of(LocalDateTime.ofInstant(
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
                    SYSTEM_ZONE));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseDurationOffset(String value) {
        String lower = value.toLowerCase();
        try {
            if (lower.endsWith("ms")) {
                long millis = Long.parseLong(lower.substring(0, lower.length() - 2));
                return Optional.of(LocalDateTime.now().plus(Duration.ofMillis(millis)));
            }
            if (lower.endsWith("s")) {
                long seconds = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Optional.of(LocalDateTime.now().plusSeconds(seconds));
            }
            if (lower.endsWith("m")) {
                long minutes = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Optional.of(LocalDateTime.now().plusMinutes(minutes));
            }
            if (lower.endsWith("h")) {
                long hours = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Optional.of(LocalDateTime.now().plusHours(hours));
            }
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<LocalDateTime> parseEpoch(String value) {
        if (!value.matches("\\d+")) {
            return Optional.empty();
        }
        try {
            long number = Long.parseLong(value);
            Instant instant = number > 9_999_999_999L
                    ? Instant.ofEpochMilli(number)
                    : Instant.ofEpochSecond(number);
            return Optional.of(LocalDateTime.ofInstant(instant, SYSTEM_ZONE));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseDateTime(String value) {
        try {
            return Optional.of(LocalDateTime.ofInstant(Instant.parse(value), SYSTEM_ZONE));
        } catch (DateTimeParseException exception) {
            // Continue with the other common formats below.
        }
        try {
            return Optional.of(LocalDateTime.ofInstant(ZonedDateTime.parse(value).toInstant(), SYSTEM_ZONE));
        } catch (DateTimeParseException exception) {
            // Continue with local date-time parsing.
        }
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<ProviderUpstreamException> findProviderUpstreamException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ProviderUpstreamException providerUpstreamException) {
                return Optional.of(providerUpstreamException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private void runStateUpdate(Long providerKeyId, StateUpdate update) {
        try {
            update.run();
        } catch (RuntimeException exception) {
            logger.warn("Failed to update provider key availability, providerKeyId={}", providerKeyId, exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @FunctionalInterface
    private interface StateUpdate {
        void run();
    }
}
