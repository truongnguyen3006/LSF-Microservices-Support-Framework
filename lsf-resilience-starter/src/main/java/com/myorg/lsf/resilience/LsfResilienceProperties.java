package com.myorg.lsf.resilience;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lsf.resilience")
public class LsfResilienceProperties {

    private boolean enabled = true;

    private Policy defaults = new Policy();

    private Map<String, Policy> instances = new LinkedHashMap<>();

    @Data
    public static class Policy {
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private Retry retry = new Retry();
        private Timeout timeout = new Timeout();
        private RateLimit rateLimit = new RateLimit();
    }

    @Data
    public static class CircuitBreaker {
        private Boolean enabled;
        private Integer slidingWindowSize;
        private Integer minimumNumberOfCalls;
        private Float failureRateThreshold;
        private Duration waitDurationInOpenState;
        private Integer permittedNumberOfCallsInHalfOpenState;
    }

    @Data
    public static class Retry {
        private Boolean enabled;
        private Integer maxAttempts;
        private Duration waitDuration;
    }

    @Data
    public static class Timeout {
        private Boolean enabled;
        private Duration duration;
        private Boolean cancelRunningFuture;
    }

    @Data
    public static class RateLimit {
        private Boolean enabled;
        private Integer limitForPeriod;
        private Duration limitRefreshPeriod;
        private Duration timeoutDuration;
    }
}
