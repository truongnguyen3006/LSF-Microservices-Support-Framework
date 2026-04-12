package com.myorg.lsf.contracts.core.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LsfErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        boolean retryable,
        String service,
        String correlationId,
        String causationId,
        String requestId,
        String traceId
) {}
