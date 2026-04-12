package com.myorg.lsf.gateway;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LsfCorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private final LsfGatewayProperties properties;

    public LsfCorrelationIdGlobalFilter(LsfGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String headerName = properties.getCorrelationHeader();
        String correlationId = firstHeader(exchange.getRequest().getHeaders(), correlationHeaders(headerName));
        String requestId = firstHeader(exchange.getRequest().getHeaders(), CoreHeaders.requestIdHeaders());

        ServerWebExchange effectiveExchange = exchange;
        if (!StringUtils.hasText(correlationId) && properties.isGenerateCorrelationId()) {
            correlationId = UUID.randomUUID().toString();
        }
        if (!StringUtils.hasText(requestId) && properties.isGenerateRequestId()) {
            requestId = StringUtils.hasText(correlationId) ? correlationId : UUID.randomUUID().toString();
        }

        if (StringUtils.hasText(correlationId) || StringUtils.hasText(requestId)) {
            ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
            if (StringUtils.hasText(correlationId)) {
                setIfAbsent(builder, exchange.getRequest().getHeaders(), headerName, correlationId);
                setIfAbsent(builder, exchange.getRequest().getHeaders(), CoreHeaders.HTTP_CORRELATION_ID, correlationId);
                setIfAbsent(builder, exchange.getRequest().getHeaders(), CoreHeaders.CORRELATION_ID, correlationId);
            }
            if (StringUtils.hasText(requestId)) {
                setIfAbsent(builder, exchange.getRequest().getHeaders(), CoreHeaders.HTTP_REQUEST_ID, requestId);
                setIfAbsent(builder, exchange.getRequest().getHeaders(), CoreHeaders.REQUEST_ID, requestId);
            }
            effectiveExchange = exchange.mutate().request(builder.build()).build();
        }

        if (StringUtils.hasText(correlationId) && properties.isEchoCorrelationIdResponse()) {
            String finalCorrelationId = correlationId;
            ServerWebExchange finalExchange = effectiveExchange;
            finalExchange.getResponse().beforeCommit(() -> {
                if (!finalExchange.getResponse().getHeaders().containsKey(headerName)) {
                    finalExchange.getResponse().getHeaders().set(headerName, finalCorrelationId);
                }
                if (!finalExchange.getResponse().getHeaders().containsKey(CoreHeaders.HTTP_CORRELATION_ID)) {
                    finalExchange.getResponse().getHeaders().set(CoreHeaders.HTTP_CORRELATION_ID, finalCorrelationId);
                }
                return Mono.empty();
            });
        }
        if (StringUtils.hasText(requestId) && properties.isEchoRequestIdResponse()) {
            String finalRequestId = requestId;
            ServerWebExchange finalExchange = effectiveExchange;
            finalExchange.getResponse().beforeCommit(() -> {
                if (!finalExchange.getResponse().getHeaders().containsKey(CoreHeaders.HTTP_REQUEST_ID)) {
                    finalExchange.getResponse().getHeaders().set(CoreHeaders.HTTP_REQUEST_ID, finalRequestId);
                }
                return Mono.empty();
            });
        }

        return chain.filter(effectiveExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private static List<String> correlationHeaders(String configured) {
        List<String> headers = new ArrayList<>();
        if (StringUtils.hasText(configured)) {
            headers.add(configured);
        }
        headers.addAll(CoreHeaders.correlationIdHeaders());
        return headers;
    }

    private static String firstHeader(HttpHeaders headers, List<String> names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static void setIfAbsent(ServerHttpRequest.Builder builder, HttpHeaders existingHeaders, String headerName, String value) {
        if (!existingHeaders.containsKey(headerName) && StringUtils.hasText(value)) {
            builder.header(headerName, value);
        }
    }
}
