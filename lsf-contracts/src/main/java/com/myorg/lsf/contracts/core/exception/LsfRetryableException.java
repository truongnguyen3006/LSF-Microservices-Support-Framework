package com.myorg.lsf.contracts.core.exception;

public class LsfRetryableException extends RuntimeException implements LsfRetryAware {

    private final String reason;

    public LsfRetryableException(String message) {
        this("RETRYABLE", message);
    }

    public LsfRetryableException(String reason, String message) {
        super(message);
        this.reason = (reason == null || reason.isBlank()) ? "RETRYABLE" : reason;
    }

    public LsfRetryableException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = (reason == null || reason.isBlank()) ? "RETRYABLE" : reason;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
