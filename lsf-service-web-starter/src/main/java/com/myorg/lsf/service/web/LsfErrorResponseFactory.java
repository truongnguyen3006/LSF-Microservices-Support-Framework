package com.myorg.lsf.service.web;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;

import java.time.Instant;

public class LsfErrorResponseFactory {

    private final String serviceName;

    public LsfErrorResponseFactory(String serviceName) {
        this.serviceName = StringUtils.hasText(serviceName) ? serviceName : "unknown-service";
    }

    public LsfErrorResponse create(
            HttpServletRequest request,
            HttpStatusCode status,
            String code,
            String message,
            boolean retryable
    ) {
        LsfRequestContext context = requestContext(request);
        HttpStatus httpStatus = HttpStatus.resolve(status.value());

        return new LsfErrorResponse(
                Instant.now(),
                status.value(),
                httpStatus == null ? "Unknown" : httpStatus.getReasonPhrase(),
                code,
                message,
                request == null ? null : request.getRequestURI(),
                retryable,
                serviceName,
                context == null ? null : context.correlationId(),
                context == null ? null : context.causationId(),
                context == null ? null : context.requestId(),
                MDC.get("traceId")
        );
    }

    public LsfRequestContext requestContext(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(LsfRequestContextFilter.REQUEST_CONTEXT_ATTRIBUTE);
            if (attribute instanceof LsfRequestContext context) {
                return context;
            }
        }
        return LsfRequestContextHolder.getContext();
    }
}
