package com.myorg.lsf.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.discovery.LsfServiceLocator;
import com.myorg.lsf.resilience.LsfResilienceComponents;
import com.myorg.lsf.resilience.LsfResilienceExecutor;
import com.myorg.lsf.resilience.LsfResiliencePolicyResolver;
import com.myorg.lsf.resilience.LsfResilienceProperties;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LsfHttpServiceClientFactoryTest {

    @AfterEach
    void tearDown() {
        LsfRequestContextHolder.clear();
        LsfTraceContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateHeadersAuthAndTraceHeaders() throws Exception {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            byte[] body = "{\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        try {
            MockHttpServletRequest incoming = new MockHttpServletRequest();
            incoming.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
            LsfRequestContextHolder.setContext(new LsfRequestContext("corr-1", "cause-1", "req-1"));

            InventoryClient client = factory(
                    serviceLocator(baseUrl(server)),
                    new MockEnvironment().withProperty("lsf.security.api-key.value", "shared-key"),
                    null
            ).createClient(InventoryClient.class);

            StatusResponse response = client.status();

            assertThat(response.value()).isEqualTo("ok");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.HTTP_CORRELATION_ID)).isEqualTo("corr-1");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.HTTP_CAUSATION_ID)).isEqualTo("cause-1");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.HTTP_REQUEST_ID)).isEqualTo("req-1");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.CORRELATION_ID)).isEqualTo("corr-1");
            assertThat(capturedHeaders.get().getFirst("X-API-Key")).isEqualTo("shared-key");
            assertThat(capturedHeaders.get().getFirst("traceparent"))
                    .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPropagateTraceHeadersFromAsyncHolderWhenNoServletRequestExists() throws Exception {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            byte[] body = "{\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        try {
            LsfRequestContextHolder.setContext(new LsfRequestContext("corr-2", "cause-2", "req-2"));
            LsfTraceContextHolder.setContext(new LsfTraceContext(java.util.Map.of(
                    CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
            )));

            InventoryClient client = factory(
                    serviceLocator(baseUrl(server)),
                    new MockEnvironment().withProperty("lsf.security.api-key.value", "shared-key"),
                    null
            ).createClient(InventoryClient.class);

            StatusResponse response = client.status();

            assertThat(response.value()).isEqualTo("ok");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.HTTP_CORRELATION_ID)).isEqualTo("corr-2");
            assertThat(capturedHeaders.get().getFirst(CoreHeaders.TRACEPARENT))
                    .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotRetryNonRetryableRemoteErrors() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            hits.incrementAndGet();
            byte[] body = """
                    {
                      "status": 422,
                      "error": "Unprocessable Entity",
                      "code": "BUSINESS_REJECTED",
                      "message": "invalid state",
                      "path": "/internal/status",
                      "retryable": false
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(422, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            InventoryClient client = factory(
                    serviceLocator(baseUrl(server)),
                    new MockEnvironment().withProperty("lsf.security.api-key.value", "shared-key"),
                    resilienceExecutor(executorService)
            ).createClient(InventoryClient.class);

            assertThatThrownBy(client::status)
                    .isInstanceOf(LsfRemoteServiceException.class)
                    .satisfies(ex -> assertThat(((LsfRemoteServiceException) ex).isRetryable()).isFalse());

            assertThat(hits.get()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
            server.stop(0);
        }
    }

    private LsfHttpServiceClientFactory factory(
            LsfServiceLocator serviceLocator,
            MockEnvironment environment,
            LsfResilienceExecutor resilienceExecutor
    ) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (resilienceExecutor != null) {
            beanFactory.registerSingleton("lsfResilienceExecutor", resilienceExecutor);
        }
        ObjectProvider<LsfResilienceExecutor> provider = beanFactory.getBeanProvider(LsfResilienceExecutor.class);

        LsfHttpClientProperties properties = new LsfHttpClientProperties();
        properties.getAuthentication().setMode(LsfClientAuthMode.AUTO);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));

        return new LsfHttpServiceClientFactory(
                RestClient.builder(),
                serviceLocator,
                provider,
                properties,
                new LsfServiceAuthenticationResolver(properties, environment),
                new ObjectMapper()
        );
    }

    private LsfServiceLocator serviceLocator(String baseUrl) {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getInstanceId()).thenReturn("inventory-1");
        when(instance.getUri()).thenReturn(URI.create(baseUrl));
        when(discoveryClient.getInstances("inventory-service")).thenReturn(List.of(instance));
        return new LsfServiceLocator(discoveryClient);
    }

    private LsfResilienceExecutor resilienceExecutor(ExecutorService executorService) {
        LsfResilienceProperties properties = new LsfResilienceProperties();
        LsfResilienceProperties.Policy policy = new LsfResilienceProperties.Policy();
        policy.getRetry().setEnabled(true);
        policy.getRetry().setMaxAttempts(3);
        policy.getRetry().setWaitDuration(Duration.ofMillis(1));
        properties.getInstances().put("inventory-http", policy);

        LsfResiliencePolicyResolver resolver = new LsfResiliencePolicyResolver(properties);
        return new LsfResilienceExecutor(new LsfResilienceComponents(resolver), executorService);
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/status", handler);
        server.start();
        return server;
    }

    private String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @LsfHttpClient(
            serviceId = "inventory-service",
            pathPrefix = "/internal",
            resilienceId = "inventory-http",
            authMode = LsfClientAuthMode.API_KEY
    )
    @HttpExchange
    interface InventoryClient {
        @GetExchange("/status")
        StatusResponse status();
    }

    record StatusResponse(String value) {
    }
}
