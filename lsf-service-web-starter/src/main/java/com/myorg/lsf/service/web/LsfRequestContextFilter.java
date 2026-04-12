package com.myorg.lsf.service.web;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LsfRequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_CONTEXT_ATTRIBUTE = LsfRequestContext.class.getName();

    private final LsfServiceWebProperties properties;

    public LsfRequestContextFilter(LsfServiceWebProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        LsfRequestContext context = buildContext(request);
        LsfTraceContext traceContext = buildTraceContext(request);
        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
        LsfRequestContextHolder.setContext(context);
        LsfTraceContextHolder.setContext(traceContext);
        putMdc(context);
        writeResponseHeaders(response, context);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LsfRequestContextHolder.clear();
            LsfTraceContextHolder.clear();
            clearMdc();
        }
    }

    private LsfRequestContext buildContext(HttpServletRequest request) {
        String correlationId = firstHeader(request, CoreHeaders.correlationIdHeaders());
        if (!StringUtils.hasText(correlationId) && properties.isGenerateCorrelationId()) {
            correlationId = UUID.randomUUID().toString();
        }

        String requestId = firstHeader(request, CoreHeaders.requestIdHeaders());
        if (!StringUtils.hasText(requestId) && properties.isGenerateRequestId()) {
            requestId = StringUtils.hasText(correlationId) ? correlationId : UUID.randomUUID().toString();
        }

        String causationId = firstHeader(request, CoreHeaders.causationIdHeaders());
        return new LsfRequestContext(correlationId, causationId, requestId);
    }

    private void writeResponseHeaders(HttpServletResponse response, LsfRequestContext context) {
        if (!properties.isEchoHeaders()) {
            return;
        }
        if (StringUtils.hasText(context.correlationId())) {
            response.setHeader(CoreHeaders.HTTP_CORRELATION_ID, context.correlationId());
        }
        if (StringUtils.hasText(context.causationId())) {
            response.setHeader(CoreHeaders.HTTP_CAUSATION_ID, context.causationId());
        }
        if (StringUtils.hasText(context.requestId())) {
            response.setHeader(CoreHeaders.HTTP_REQUEST_ID, context.requestId());
        }
    }

    private static String firstHeader(HttpServletRequest request, List<String> names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static LsfTraceContext buildTraceContext(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String header : CoreHeaders.traceHeaders()) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                headers.put(header, value);
            }
        }
        return headers.isEmpty() ? null : new LsfTraceContext(headers);
    }

    private static void putMdc(LsfRequestContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.correlationId())) {
            MDC.put("corrId", context.correlationId());
            MDC.put("correlationId", context.correlationId());
        }
        if (StringUtils.hasText(context.causationId())) {
            MDC.put("causationId", context.causationId());
        }
        if (StringUtils.hasText(context.requestId())) {
            MDC.put("requestId", context.requestId());
        }
    }

    private static void clearMdc() {
        MDC.remove("corrId");
        MDC.remove("correlationId");
        MDC.remove("causationId");
        MDC.remove("requestId");
    }
}
