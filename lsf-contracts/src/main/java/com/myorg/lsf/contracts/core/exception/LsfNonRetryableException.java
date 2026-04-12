package com.myorg.lsf.contracts.core.exception;

public class LsfNonRetryableException extends RuntimeException implements LsfRetryAware {

    private final String reason;

    // Backward compatible: old constructor -> default reason
    public LsfNonRetryableException(String message) {
        this("NON_RETRYABLE", message);
    }

    public LsfNonRetryableException(String reason, String message) {
        super(message);
        this.reason = (reason == null || reason.isBlank()) ? "NON_RETRYABLE" : reason;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
