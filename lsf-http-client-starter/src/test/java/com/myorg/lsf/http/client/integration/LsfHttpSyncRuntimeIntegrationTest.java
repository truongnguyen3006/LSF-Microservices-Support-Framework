package com.myorg.lsf.http.client.integration;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import com.myorg.lsf.contracts.core.exception.LsfRetryableException;
import com.myorg.lsf.discovery.LsfServiceLocator;
import com.myorg.lsf.http.client.EnableLsfHttpClients;
import com.myorg.lsf.http.client.LsfRemoteServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = LsfHttpSyncRuntimeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=inventory-service",
                "lsf.discovery.enabled=false",
                "lsf.service.web.enabled=true",
                "lsf.security.enabled=true",
                "lsf.security.mode=api-key",
                "lsf.security.api-key.header-name=X-API-Key",
                "lsf.security.api-key.value=test-key",
                "lsf.http.client.authentication.mode=api-key",
                "lsf.http.client.authentication.api-key.value=test-key",
                "lsf.resilience.instances.inventory-http.retry.enabled=true",
                "lsf.resilience.instances.inventory-http.retry.max-attempts=3",
                "lsf.resilience.instances.inventory-http.retry.wait-duration=5ms"
        }
)
class LsfHttpSyncRuntimeIntegrationTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Autowired
    InventoryRuntimeClient client;

    @Autowired
    RuntimeScenarioState state;

    @BeforeEach
    void setUp() {
        state.reset();
        LsfRequestContextHolder.clear();
        LsfTraceContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        LsfRequestContextHolder.clear();
        LsfTraceContextHolder.clear();
    }

    @Test
    void shouldPropagateHeadersAuthenticateAndReturnContextFromServiceWeb() {
        LsfRequestContextHolder.setContext(new LsfRequestContext("corr-success", "cause-success", "req-success"));
        LsfTraceContextHolder.setContext(new LsfTraceContext(Map.of(CoreHeaders.TRACEPARENT, TRACEPARENT)));

        RuntimeResponse response = client.success();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.correlationId()).isEqualTo("corr-success");
        assertThat(response.causationId()).isEqualTo("cause-success");
        assertThat(response.requestId()).isEqualTo("req-success");
        assertThat(response.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(response.principal()).isEqualTo("lsf-internal");
        assertThat(state.successAttempts().get()).isEqualTo(1);
    }

    @Test
    void shouldRetryRetryableRemoteErrorsUntilSuccess() {
        LsfRequestContextHolder.setContext(new LsfRequestContext("corr-retry", "cause-retry", "req-retry"));

        RuntimeResponse response = client.retryable();

        assertThat(response.status()).isEqualTo("recovered");
        assertThat(response.correlationId()).isEqualTo("corr-retry");
        assertThat(response.requestId()).isEqualTo("req-retry");
        assertThat(response.attempt()).isEqualTo(3);
        assertThat(state.retryableAttempts().get()).isEqualTo(3);
    }

    @Test
    void shouldDecodeNonRetryableErrorsWithoutRetrying() {
        LsfRequestContextHolder.setContext(new LsfRequestContext("corr-business", "cause-business", "req-business"));

        assertThatThrownBy(client::nonRetryable)
                .isInstanceOf(LsfRemoteServiceException.class)
                .satisfies(ex -> {
                    LsfRemoteServiceException remote = (LsfRemoteServiceException) ex;
                    assertThat(remote.getStatus()).isEqualTo(422);
                    assertThat(remote.getCode()).isEqualTo("BUSINESS_REJECTED");
                    assertThat(remote.isRetryable()).isFalse();
                    assertThat(remote.getErrorResponse()).isNotNull();
                    assertThat(remote.getErrorResponse().service()).isEqualTo("inventory-service");
                    assertThat(remote.getErrorResponse().correlationId()).isEqualTo("corr-business");
                    assertThat(remote.getErrorResponse().causationId()).isEqualTo("cause-business");
                    assertThat(remote.getErrorResponse().requestId()).isEqualTo("req-business");
                });

        assertThat(state.nonRetryableAttempts().get()).isEqualTo(1);
    }

    @SpringBootApplication
    @EnableLsfHttpClients(basePackageClasses = InventoryRuntimeClient.class)
    @Import(InventoryRuntimeController.class)
    static class TestApplication {

        @Bean
        RuntimeScenarioState runtimeScenarioState() {
            return new RuntimeScenarioState();
        }

        @Bean
        LsfServiceLocator lsfServiceLocator(Environment environment) {
            DiscoveryClient discoveryClient = new DiscoveryClient() {
                @Override
                public String description() {
                    return "local-runtime-discovery";
                }

                @Override
                public List<ServiceInstance> getInstances(String serviceId) {
                    Integer port = environment.getProperty("local.server.port", Integer.class);
                    if (!"inventory-service".equals(serviceId) || port == null || port <= 0) {
                        return List.of();
                    }
                    return List.of(new DefaultServiceInstance("inventory-local", serviceId, "localhost", port, false));
                }

                @Override
                public List<String> getServices() {
                    return List.of("inventory-service");
                }
            };
            return new LsfServiceLocator(discoveryClient);
        }
    }

    @RestController
    @RequestMapping("/internal/runtime")
    static class InventoryRuntimeController {

        private final RuntimeScenarioState state;

        InventoryRuntimeController(RuntimeScenarioState state) {
            this.state = state;
        }

        @GetMapping("/success")
        RuntimeResponse success(Authentication authentication) {
            state.successAttempts().incrementAndGet();
            return response("ok", 1, authentication);
        }

        @GetMapping("/retryable")
        RuntimeResponse retryable(Authentication authentication) {
            int attempt = state.retryableAttempts().incrementAndGet();
            if (attempt < 3) {
                throw new LsfRetryableException("INVENTORY_TEMPORARY", "inventory is warming up");
            }
            return response("recovered", attempt, authentication);
        }

        @GetMapping("/non-retryable")
        RuntimeResponse nonRetryable() {
            state.nonRetryableAttempts().incrementAndGet();
            throw new LsfNonRetryableException("BUSINESS_REJECTED", "inventory rejected the request");
        }

        private RuntimeResponse response(String status, int attempt, Authentication authentication) {
            return new RuntimeResponse(
                    status,
                    requestContextValue(LsfRequestContextHolder.getContext() == null ? null : LsfRequestContextHolder.getContext().correlationId()),
                    requestContextValue(LsfRequestContextHolder.getContext() == null ? null : LsfRequestContextHolder.getContext().causationId()),
                    requestContextValue(LsfRequestContextHolder.getContext() == null ? null : LsfRequestContextHolder.getContext().requestId()),
                    LsfTraceContextHolder.getContext() == null ? null : LsfTraceContextHolder.getContext().header(CoreHeaders.TRACEPARENT),
                    authentication == null ? null : authentication.getName(),
                    attempt
            );
        }

        private String requestContextValue(String value) {
            return StringUtils.hasText(value) ? value : null;
        }
    }

    static final class RuntimeScenarioState {
        private final AtomicInteger successAttempts = new AtomicInteger();
        private final AtomicInteger retryableAttempts = new AtomicInteger();
        private final AtomicInteger nonRetryableAttempts = new AtomicInteger();

        AtomicInteger successAttempts() {
            return successAttempts;
        }

        AtomicInteger retryableAttempts() {
            return retryableAttempts;
        }

        AtomicInteger nonRetryableAttempts() {
            return nonRetryableAttempts;
        }

        void reset() {
            successAttempts.set(0);
            retryableAttempts.set(0);
            nonRetryableAttempts.set(0);
        }
    }
}
