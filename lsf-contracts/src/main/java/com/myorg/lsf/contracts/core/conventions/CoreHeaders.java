package com.myorg.lsf.contracts.core.conventions;

import java.util.List;

public final class CoreHeaders {
    private CoreHeaders() {}

    public static final String CORRELATION_ID = "lsf-correlation-id";
    public static final String EVENT_ID = "lsf-event-id";
    public static final String EVENT_TYPE = "lsf-event-type";
    public static final String CAUSATION_ID = "lsf-causation-id";
    public static final String REQUEST_ID = "lsf-request-id";
    public static final String HTTP_CORRELATION_ID = "correlation-id";
    public static final String HTTP_CAUSATION_ID = "causation-id";
    public static final String HTTP_REQUEST_ID = "request-id";
    public static final String LEGACY_GATEWAY_CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String B3 = "b3";
    public static final String X_B3_TRACE_ID = "x-b3-traceid";
    public static final String X_B3_SPAN_ID = "x-b3-spanid";
    public static final String X_B3_PARENT_SPAN_ID = "x-b3-parentspanid";
    public static final String X_B3_SAMPLED = "x-b3-sampled";
    public static final String X_B3_FLAGS = "x-b3-flags";

    public static List<String> correlationIdHeaders() {
        return List.of(HTTP_CORRELATION_ID, CORRELATION_ID, LEGACY_GATEWAY_CORRELATION_ID);
    }

    public static List<String> causationIdHeaders() {
        return List.of(HTTP_CAUSATION_ID, CAUSATION_ID);
    }

    public static List<String> requestIdHeaders() {
        return List.of(HTTP_REQUEST_ID, REQUEST_ID);
    }

    public static List<String> traceHeaders() {
        return List.of(
                TRACEPARENT,
                TRACESTATE,
                B3,
                X_B3_TRACE_ID,
                X_B3_SPAN_ID,
                X_B3_PARENT_SPAN_ID,
                X_B3_SAMPLED,
                X_B3_FLAGS
        );
    }
}
