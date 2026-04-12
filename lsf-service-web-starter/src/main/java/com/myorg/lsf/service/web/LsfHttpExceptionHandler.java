package com.myorg.lsf.service.web;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import com.myorg.lsf.contracts.core.exception.LsfRetryableException;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeoutException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LsfHttpExceptionHandler {

    private final LsfErrorResponseFactory errorResponseFactory;

    public LsfHttpExceptionHandler(LsfErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<LsfErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", messageOrDefault(ex, "Request is invalid"), false);
    }

    @ExceptionHandler(LsfNonRetryableException.class)
    public ResponseEntity<LsfErrorResponse> handleNonRetryable(LsfNonRetryableException ex, HttpServletRequest request) {
        String code = StringUtils.hasText(ex.getReason()) ? ex.getReason() : "NON_RETRYABLE";
        return response(request, HttpStatus.UNPROCESSABLE_ENTITY, code, ex.getMessage(), false);
    }

    @ExceptionHandler(LsfRetryableException.class)
    public ResponseEntity<LsfErrorResponse> handleRetryable(LsfRetryableException ex, HttpServletRequest request) {
        String code = StringUtils.hasText(ex.getReason()) ? ex.getReason() : "RETRYABLE";
        return response(request, HttpStatus.SERVICE_UNAVAILABLE, code, ex.getMessage(), true);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<LsfErrorResponse> handleTimeout(TimeoutException ex, HttpServletRequest request) {
        return response(request, HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT", messageOrDefault(ex, "Call timed out"), true);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<LsfErrorResponse> handleCircuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        return response(request, HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", ex.getMessage(), true);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<LsfErrorResponse> handleRateLimit(RequestNotPermitted ex, HttpServletRequest request) {
        return response(request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", ex.getMessage(), true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<LsfErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return response(request, status, status.name(), messageOrDefault(ex, status.getReasonPhrase()), isRetryable(status));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LsfErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return response(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                messageOrDefault(ex, "Unexpected server error"),
                true
        );
    }

    private ResponseEntity<LsfErrorResponse> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            boolean retryable
    ) {
        return ResponseEntity.status(status)
                .body(errorResponseFactory.create(request, status, code, message, retryable));
    }

    private static String messageOrDefault(Exception ex, String fallback) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : fallback;
    }

    private static boolean isRetryable(HttpStatus status) {
        return status == HttpStatus.REQUEST_TIMEOUT
                || status == HttpStatus.TOO_EARLY
                || status == HttpStatus.TOO_MANY_REQUESTS
                || status.is5xxServerError();
    }
}
