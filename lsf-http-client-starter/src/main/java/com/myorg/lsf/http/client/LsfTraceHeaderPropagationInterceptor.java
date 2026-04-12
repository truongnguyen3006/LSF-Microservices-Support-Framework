package com.myorg.lsf.http.client;

import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;

public class LsfTraceHeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    private static final List<String> TRACE_HEADERS = List.of(
            "traceparent",
            "tracestate",
            "b3",
            "x-b3-traceid",
            "x-b3-spanid",
            "x-b3-parentspanid",
            "x-b3-sampled",
            "x-b3-flags"
    );

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        propagateFromHolder(request);
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest incoming = servletRequestAttributes.getRequest();
            for (String header : TRACE_HEADERS) {
                if (!request.getHeaders().containsKey(header)) {
                    String value = incoming.getHeader(header);
                    if (StringUtils.hasText(value)) {
                        request.getHeaders().set(header, value);
                    }
                }
            }
        }
        synthesizeTraceparentFromMdc(request);
        return execution.execute(request, body);
    }

    private static void propagateFromHolder(HttpRequest request) {
        LsfTraceContext context = LsfTraceContextHolder.getContext();
        if (context == null || context.isEmpty()) {
            return;
        }

        for (String header : TRACE_HEADERS) {
            if (request.getHeaders().containsKey(header)) {
                continue;
            }
            String value = context.header(header);
            if (StringUtils.hasText(value)) {
                request.getHeaders().set(header, value);
            }
        }
    }

    private static void synthesizeTraceparentFromMdc(HttpRequest request) {
        if (request.getHeaders().containsKey(CoreHeaders.TRACEPARENT)) {
            return;
        }

        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (!isHex(traceId, 32) || !isHex(spanId, 16)) {
            return;
        }

        request.getHeaders().set(CoreHeaders.TRACEPARENT, "00-" + traceId.toLowerCase() + "-" + spanId.toLowerCase() + "-01");
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
