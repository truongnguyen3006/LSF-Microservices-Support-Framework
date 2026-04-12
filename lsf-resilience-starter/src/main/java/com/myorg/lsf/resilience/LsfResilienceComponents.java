package com.myorg.lsf.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LsfResilienceComponents {

    private final LsfResiliencePolicyResolver resolver;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Retry> retries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TimeLimiter> timeLimiters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public LsfResilienceComponents(LsfResiliencePolicyResolver resolver) {
        this.resolver = resolver;
    }

    public LsfResolvedResiliencePolicy policy(String instanceName) {
        return resolver.resolve(normalize(instanceName));
    }

    public CircuitBreaker circuitBreaker(String instanceName) {
        String normalized = normalize(instanceName);
        return circuitBreakers.computeIfAbsent(normalized,
                key -> CircuitBreaker.of(key, resolver.resolve(key).circuitBreakerConfig()));
    }

    public Retry retry(String instanceName) {
        String normalized = normalize(instanceName);
        return retries.computeIfAbsent(normalized,
                key -> Retry.of(key, resolver.resolve(key).retryConfig()));
    }

    public TimeLimiter timeLimiter(String instanceName) {
        String normalized = normalize(instanceName);
        return timeLimiters.computeIfAbsent(normalized,
                key -> TimeLimiter.of(key, resolver.resolve(key).timeLimiterConfig()));
    }

    public RateLimiter rateLimiter(String instanceName) {
        String normalized = normalize(instanceName);
        return rateLimiters.computeIfAbsent(normalized,
                key -> RateLimiter.of(key, resolver.resolve(key).rateLimiterConfig()));
    }

    private static String normalize(String instanceName) {
        return (instanceName == null || instanceName.isBlank()) ? "default" : instanceName.trim();
    }
}
