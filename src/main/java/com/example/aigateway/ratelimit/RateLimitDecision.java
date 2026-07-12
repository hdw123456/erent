package com.example.aigateway.ratelimit;

/** Result of one rate-limit evaluation. */
public record RateLimitDecision(
        boolean allowed,
        String dimension,
        String identifier,
        long current,
        int limit,
        int windowSeconds
) {
    public static RateLimitDecision allowed(
            String dimension,
            String identifier,
            long current,
            int limit,
            int windowSeconds
    ) {
        return new RateLimitDecision(true, dimension, identifier, current, limit, windowSeconds);
    }

    public static RateLimitDecision blocked(
            String dimension,
            String identifier,
            long current,
            int limit,
            int windowSeconds
    ) {
        return new RateLimitDecision(false, dimension, identifier, current, limit, windowSeconds);
    }
}
