package com.myorg.lsf.gateway;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LsfCorrelationIdGlobalFilterTest {

    @Test
    void shouldGenerateAndEchoCorrelationId() {
        LsfGatewayProperties properties = new LsfGatewayProperties();
        LsfCorrelationIdGlobalFilter filter = new LsfCorrelationIdGlobalFilter(properties);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/demo").build());
        AtomicReference<ServerHttpRequest> requestRef = new AtomicReference<>();

        GatewayFilterChain chain = serverWebExchange -> {
            requestRef.set(serverWebExchange.getRequest());
            return serverWebExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        String correlationId = requestRef.get().getHeaders().getFirst("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        assertThat(requestRef.get().getHeaders().getFirst(CoreHeaders.HTTP_CORRELATION_ID)).isEqualTo(correlationId);
        assertThat(requestRef.get().getHeaders().getFirst(CoreHeaders.HTTP_REQUEST_ID)).isEqualTo(correlationId);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlationId);
        assertThat(exchange.getResponse().getHeaders().getFirst(CoreHeaders.HTTP_CORRELATION_ID)).isEqualTo(correlationId);
        assertThat(exchange.getResponse().getHeaders().getFirst(CoreHeaders.HTTP_REQUEST_ID)).isEqualTo(correlationId);
    }

    @Test
    void shouldReuseCanonicalHeadersWhenAlreadyPresent() {
        LsfGatewayProperties properties = new LsfGatewayProperties();
        LsfCorrelationIdGlobalFilter filter = new LsfCorrelationIdGlobalFilter(properties);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/demo")
                        .header(CoreHeaders.HTTP_CORRELATION_ID, "corr-canonical")
                        .header(CoreHeaders.HTTP_REQUEST_ID, "req-canonical")
                        .build()
        );
        AtomicReference<ServerHttpRequest> requestRef = new AtomicReference<>();

        GatewayFilterChain chain = serverWebExchange -> {
            requestRef.set(serverWebExchange.getRequest());
            return serverWebExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(requestRef.get().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-canonical");
        assertThat(requestRef.get().getHeaders().getFirst(CoreHeaders.HTTP_CORRELATION_ID)).isEqualTo("corr-canonical");
        assertThat(requestRef.get().getHeaders().getFirst(CoreHeaders.HTTP_REQUEST_ID)).isEqualTo("req-canonical");
    }
}
