package com.myorg.lsf.resilience;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LsfResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfResilienceAutoConfiguration.class));

    @Test
    void shouldRetryUntilSuccess() {
        runner.withPropertyValues(
                        "lsf.resilience.instances.demo.retry.enabled=true",
                        "lsf.resilience.instances.demo.retry.max-attempts=3",
                        "lsf.resilience.instances.demo.retry.wait-duration=1ms"
                )
                .run(context -> {
                    LsfResilienceExecutor executor = context.getBean(LsfResilienceExecutor.class);
                    AtomicInteger attempts = new AtomicInteger();

                    String value = executor.execute("demo", () -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < 3) {
                            throw new IllegalStateException("boom");
                        }
                        return "ok";
                    });

                    assertThat(value).isEqualTo("ok");
                    assertThat(attempts.get()).isEqualTo(3);
                });
    }

    @Test
    void shouldOpenCircuitBreakerAfterFailures() {
        runner.withPropertyValues(
                        "lsf.resilience.instances.cb.circuit-breaker.enabled=true",
                        "lsf.resilience.instances.cb.circuit-breaker.sliding-window-size=2",
                        "lsf.resilience.instances.cb.circuit-breaker.minimum-number-of-calls=2",
                        "lsf.resilience.instances.cb.circuit-breaker.failure-rate-threshold=50"
                )
                .run(context -> {
                    LsfResilienceExecutor executor = context.getBean(LsfResilienceExecutor.class);

                    assertThatThrownBy(() -> executor.execute("cb", () -> {
                        throw new IllegalStateException("first");
                    })).isInstanceOf(IllegalStateException.class);

                    assertThatThrownBy(() -> executor.execute("cb", () -> {
                        throw new IllegalStateException("second");
                    })).isInstanceOf(IllegalStateException.class);

                    assertThatThrownBy(() -> executor.execute("cb", () -> "never"))
                            .isInstanceOf(CallNotPermittedException.class);
                });
    }

    @Test
    void shouldApplyTimeoutToSlowCalls() {
        runner.withPropertyValues(
                        "lsf.resilience.instances.slow.timeout.enabled=true",
                        "lsf.resilience.instances.slow.timeout.duration=50ms"
                )
                .run(context -> {
                    LsfResilienceExecutor executor = context.getBean(LsfResilienceExecutor.class);

                    assertThatThrownBy(() -> executor.executeCallable("slow", () -> {
                        Thread.sleep(200);
                        return "late";
                    })).isInstanceOf(TimeoutException.class);
                });
    }

    @Test
    void shouldRejectWhenRateLimitExceeded() {
        runner.withPropertyValues(
                        "lsf.resilience.instances.rl.rate-limit.enabled=true",
                        "lsf.resilience.instances.rl.rate-limit.limit-for-period=1",
                        "lsf.resilience.instances.rl.rate-limit.limit-refresh-period=5s",
                        "lsf.resilience.instances.rl.rate-limit.timeout-duration=0ms"
                )
                .run(context -> {
                    LsfResilienceExecutor executor = context.getBean(LsfResilienceExecutor.class);

                    assertThat(executor.execute("rl", () -> "first")).isEqualTo("first");
                    assertThatThrownBy(() -> executor.execute("rl", () -> "second"))
                            .isInstanceOf(RequestNotPermitted.class);
                });
    }

    @Test
    void shouldNotRetryNonRetryableExceptions() {
        runner.withPropertyValues(
                        "lsf.resilience.instances.http.retry.enabled=true",
                        "lsf.resilience.instances.http.retry.max-attempts=3",
                        "lsf.resilience.instances.http.retry.wait-duration=1ms"
                )
                .run(context -> {
                    LsfResilienceExecutor executor = context.getBean(LsfResilienceExecutor.class);
                    AtomicInteger attempts = new AtomicInteger();

                    assertThatThrownBy(() -> executor.execute("http", () -> {
                        attempts.incrementAndGet();
                        throw new LsfNonRetryableException("BAD_REQUEST", "invalid input");
                    })).isInstanceOf(LsfNonRetryableException.class);

                    assertThat(attempts.get()).isEqualTo(1);
                });
    }
}
