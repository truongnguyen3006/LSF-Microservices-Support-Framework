package com.myorg.lsf.template.integration.http;

public record DependencyCapabilitiesResponse(
        String serviceId,
        String status,
        String correlationId,
        String requestId
) {
}
