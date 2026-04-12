package com.myorg.lsf.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LsfErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final LsfErrorResponseFactory errorResponseFactory;

    public LsfErrorResponseWriter(ObjectMapper objectMapper, LsfErrorResponseFactory errorResponseFactory) {
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message,
            boolean retryable
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        LsfErrorResponse body = errorResponseFactory.create(request, status, code, message, retryable);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
