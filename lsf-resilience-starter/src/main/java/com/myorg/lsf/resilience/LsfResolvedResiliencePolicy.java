package com.myorg.lsf.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

record LsfResolvedResiliencePolicy(
        boolean circuitBreakerEnabled,
        CircuitBreakerConfig circuitBreakerConfig,
        boolean retryEnabled,
        RetryConfig retryConfig,
        boolean timeoutEnabled,
        TimeLimiterConfig timeLimiterConfig,
        boolean rateLimitEnabled,
        RateLimiterConfig rateLimiterConfig
) {
}
