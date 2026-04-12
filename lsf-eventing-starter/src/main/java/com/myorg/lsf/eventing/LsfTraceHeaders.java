package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LsfTraceHeaders {

    private LsfTraceHeaders() {
    }

    public static Map<String, String> captureCurrentTraceHeaders() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();

        LsfTraceContext context = LsfTraceContextHolder.getContext();
        if (context != null && !context.isEmpty()) {
            for (String header : CoreHeaders.traceHeaders()) {
                String value = context.header(header);
                if (StringUtils.hasText(value)) {
                    headers.put(header, value);
                }
            }
        }

        maybeAddTraceparentFromMdc(headers);

        if (headers.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(headers);
    }

    public static void enrichEnvelope(EventEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        if (envelope.getTraceHeaders() != null && !envelope.getTraceHeaders().isEmpty()) {
            return;
        }

        Map<String, String> traceHeaders = captureCurrentTraceHeaders();
        if (!traceHeaders.isEmpty()) {
            envelope.setTraceHeaders(traceHeaders);
        }
    }

    public static void writeToKafkaHeaders(Headers headers, EventEnvelope envelope) {
        if (headers == null || envelope == null || envelope.getTraceHeaders() == null || envelope.getTraceHeaders().isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : envelope.getTraceHeaders().entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                continue;
            }
            headers.remove(name);
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void maybeAddTraceparentFromMdc(Map<String, String> headers) {
        if (headers.containsKey(CoreHeaders.TRACEPARENT)) {
            return;
        }

        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (!isHex(traceId, 32) || !isHex(spanId, 16)) {
            return;
        }

        headers.put(CoreHeaders.TRACEPARENT, "00-" + traceId.toLowerCase() + "-" + spanId.toLowerCase() + "-01");
    }

    private static boolean isHex(String value, int expectedLength) {
        if (!StringUtils.hasText(value) || value.length() != expectedLength) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            boolean lower = ch >= 'a' && ch <= 'f';
            boolean upper = ch >= 'A' && ch <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
