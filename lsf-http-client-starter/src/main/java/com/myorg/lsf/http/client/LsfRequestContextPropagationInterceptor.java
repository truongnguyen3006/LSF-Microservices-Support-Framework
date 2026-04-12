package com.myorg.lsf.http.client;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

public class LsfRequestContextPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        LsfRequestContext current = LsfRequestContextHolder.getContext();
        boolean created = false;
        if (current == null) {
            String correlationId = UUID.randomUUID().toString();
            current = new LsfRequestContext(correlationId, null, correlationId);
            LsfRequestContextHolder.setContext(current);
            created = true;
        }

        String correlationId = StringUtils.hasText(current.correlationId())
                ? current.correlationId()
                : (StringUtils.hasText(current.requestId()) ? current.requestId() : UUID.randomUUID().toString());
        String requestId = StringUtils.hasText(current.requestId()) ? current.requestId() : correlationId;

        setIfAbsent(request, CoreHeaders.HTTP_CORRELATION_ID, correlationId);
        setIfAbsent(request, CoreHeaders.HTTP_REQUEST_ID, requestId);
        setIfAbsent(request, CoreHeaders.CORRELATION_ID, correlationId);
        setIfAbsent(request, CoreHeaders.REQUEST_ID, requestId);
        if (StringUtils.hasText(current.causationId())) {
            setIfAbsent(request, CoreHeaders.HTTP_CAUSATION_ID, current.causationId());
            setIfAbsent(request, CoreHeaders.CAUSATION_ID, current.causationId());
        }

        try {
            return execution.execute(request, body);
        } finally {
            if (created) {
                LsfRequestContextHolder.clear();
            }
        }
    }

    private static void setIfAbsent(HttpRequest request, String headerName, String value) {
        if (!request.getHeaders().containsKey(headerName) && StringUtils.hasText(value)) {
            request.getHeaders().set(headerName, value);
        }
    }
}
