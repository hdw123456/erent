package com.example.aigateway.ratelimit;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Atomically enforces a Redis-backed fixed request window. */
@Service
public class FixedWindowRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(FixedWindowRateLimiter.class);
    private static final String SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final StringRedisTemplate redisTemplate;
    private final GatewayRateLimitProperties properties;
    private final DefaultRedisScript<Long> script;

    public FixedWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(SCRIPT, Long.class);
    }

    /** Increments one window key and returns whether its configured limit was exceeded. */
    public RateLimitDecision check(
            String dimension,
            String identifier,
            int limit,
            int windowSeconds
    ) {
        if (limit <= 0 || windowSeconds <= 0) {
            return RateLimitDecision.allowed(dimension, identifier, 0, limit, windowSeconds);
        }

        String key = buildKey(dimension, identifier, windowSeconds);
        try {
            Long current = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(windowSeconds),
                    String.valueOf(limit)
            );
            long count = current == null ? 0 : current;
            if (count > limit) {
                return RateLimitDecision.blocked(dimension, identifier, count, limit, windowSeconds);
            }
            return RateLimitDecision.allowed(dimension, identifier, count, limit, windowSeconds);
        } catch (RuntimeException exception) {
            if (properties.isFailOpen()) {
                logger.warn("Rate limit check failed, allow request, dimension={}", dimension, exception);
                return RateLimitDecision.allowed(dimension, identifier, 0, limit, windowSeconds);
            }
            throw exception;
        }
    }

    String buildKey(String dimension, String identifier, int windowSeconds) {
        long windowId = Instant.now().getEpochSecond() / windowSeconds;
        return "rl:fixed:"
                + sanitize(dimension)
                + ":"
                + sanitize(identifier)
                + ":"
                + windowSeconds
                + "s:"
                + windowId;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
