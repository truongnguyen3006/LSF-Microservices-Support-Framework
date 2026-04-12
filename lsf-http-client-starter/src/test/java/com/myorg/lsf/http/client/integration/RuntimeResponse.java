package com.myorg.lsf.http.client.integration;

public record RuntimeResponse(
        String status,
        String correlationId,
        String causationId,
        String requestId,
        String traceparent,
        String principal,
        int attempt
) {
}
