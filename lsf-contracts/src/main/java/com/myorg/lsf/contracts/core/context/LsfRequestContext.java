package com.myorg.lsf.contracts.core.context;

public record LsfRequestContext(
        String correlationId,
        String causationId,
        String requestId
) {}
