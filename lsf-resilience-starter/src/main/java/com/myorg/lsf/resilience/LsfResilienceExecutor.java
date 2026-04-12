package com.myorg.lsf.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class LsfResilienceExecutor {

    private final LsfResilienceComponents components;
    private final ExecutorService executorService;

    public LsfResilienceExecutor(LsfResilienceComponents components, ExecutorService executorService) {
        this.components = components;
        this.executorService = executorService;
    }

    public <T> T execute(String instanceName, CheckedSupplier<T> supplier) {
        try {
            return executeCallable(instanceName, supplier::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Resilience execution failed for instance=" + instanceName, e);
        }
    }

    public <T> T executeCallable(String instanceName, Callable<T> callable) throws Exception {
        LsfResolvedResiliencePolicy policy = components.policy(instanceName);
        Callable<T> decorated = callable;

        if (policy.timeoutEnabled()) {
            TimeLimiter timeLimiter = components.timeLimiter(instanceName);
            Callable<T> delegate = decorated;
            decorated = () -> timeLimiter.executeFutureSupplier(() -> executorService.submit(delegate));
        }
        if (policy.rateLimitEnabled()) {
            RateLimiter rateLimiter = components.rateLimiter(instanceName);
            decorated = RateLimiter.decorateCallable(rateLimiter, decorated);
        }
        if (policy.circuitBreakerEnabled()) {
            CircuitBreaker circuitBreaker = components.circuitBreaker(instanceName);
            decorated = CircuitBreaker.decorateCallable(circuitBreaker, decorated);
        }
        if (policy.retryEnabled()) {
            Retry retry = components.retry(instanceName);
            decorated = Retry.decorateCallable(retry, decorated);
        }

        return decorated.call();
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
