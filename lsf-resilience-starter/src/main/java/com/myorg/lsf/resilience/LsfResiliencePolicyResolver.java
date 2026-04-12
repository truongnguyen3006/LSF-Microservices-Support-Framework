package com.myorg.lsf.resilience;

import com.myorg.lsf.contracts.core.exception.LsfRetryDecisions;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import java.time.Duration;

public class LsfResiliencePolicyResolver {

    private final LsfResilienceProperties properties;

    public LsfResiliencePolicyResolver(LsfResilienceProperties properties) {
        this.properties = properties;
    }

    public LsfResolvedResiliencePolicy resolve(String instanceName) {
        LsfResilienceProperties.Policy defaults = properties.getDefaults();
        LsfResilienceProperties.Policy instance = properties.getInstances().getOrDefault(instanceName, new LsfResilienceProperties.Policy());

        boolean circuitBreakerEnabled = pick(
                instance.getCircuitBreaker().getEnabled(),
                defaults.getCircuitBreaker().getEnabled(),
                false
        );
        boolean retryEnabled = pick(instance.getRetry().getEnabled(), defaults.getRetry().getEnabled(), false);
        boolean timeoutEnabled = pick(instance.getTimeout().getEnabled(), defaults.getTimeout().getEnabled(), false);
        boolean rateLimitEnabled = pick(instance.getRateLimit().getEnabled(), defaults.getRateLimit().getEnabled(), false);

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(pick(instance.getCircuitBreaker().getSlidingWindowSize(),
                        defaults.getCircuitBreaker().getSlidingWindowSize(), 20))
                .minimumNumberOfCalls(pick(instance.getCircuitBreaker().getMinimumNumberOfCalls(),
                        defaults.getCircuitBreaker().getMinimumNumberOfCalls(), 5))
                .failureRateThreshold(pick(instance.getCircuitBreaker().getFailureRateThreshold(),
                        defaults.getCircuitBreaker().getFailureRateThreshold(), 50.0f))
                .waitDurationInOpenState(pick(instance.getCircuitBreaker().getWaitDurationInOpenState(),
                        defaults.getCircuitBreaker().getWaitDurationInOpenState(), Duration.ofSeconds(30)))
                .permittedNumberOfCallsInHalfOpenState(pick(instance.getCircuitBreaker().getPermittedNumberOfCallsInHalfOpenState(),
                        defaults.getCircuitBreaker().getPermittedNumberOfCallsInHalfOpenState(), 3))
                .build();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(pick(instance.getRetry().getMaxAttempts(), defaults.getRetry().getMaxAttempts(), 3))
                .waitDuration(pick(instance.getRetry().getWaitDuration(), defaults.getRetry().getWaitDuration(), Duration.ofMillis(100)))
                .retryOnException(LsfRetryDecisions::isRetryable)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(pick(instance.getTimeout().getDuration(), defaults.getTimeout().getDuration(), Duration.ofSeconds(2)))
                .cancelRunningFuture(pick(instance.getTimeout().getCancelRunningFuture(),
                        defaults.getTimeout().getCancelRunningFuture(), true))
                .build();

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(pick(instance.getRateLimit().getLimitForPeriod(),
                        defaults.getRateLimit().getLimitForPeriod(), 100))
                .limitRefreshPeriod(pick(instance.getRateLimit().getLimitRefreshPeriod(),
                        defaults.getRateLimit().getLimitRefreshPeriod(), Duration.ofSeconds(1)))
                .timeoutDuration(pick(instance.getRateLimit().getTimeoutDuration(),
                        defaults.getRateLimit().getTimeoutDuration(), Duration.ZERO))
                .build();

        return new LsfResolvedResiliencePolicy(
                circuitBreakerEnabled,
                circuitBreakerConfig,
                retryEnabled,
                retryConfig,
                timeoutEnabled,
                timeLimiterConfig,
                rateLimitEnabled,
                rateLimiterConfig
        );
    }

    private static boolean pick(Boolean instanceValue, Boolean defaultValue, boolean hardDefault) {
        if (instanceValue != null) {
            return instanceValue;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        return hardDefault;
    }

    private static int pick(Integer instanceValue, Integer defaultValue, int hardDefault) {
        if (instanceValue != null) {
            return instanceValue;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        return hardDefault;
    }

    private static float pick(Float instanceValue, Float defaultValue, float hardDefault) {
        if (instanceValue != null) {
            return instanceValue;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        return hardDefault;
    }

    private static Duration pick(Duration instanceValue, Duration defaultValue, Duration hardDefault) {
        if (instanceValue != null) {
            return instanceValue;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        return hardDefault;
    }
}
