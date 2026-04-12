package com.myorg.lsf.eventing.exception;

public class LsfRetryableException extends com.myorg.lsf.contracts.core.exception.LsfRetryableException {
    public LsfRetryableException(String msg) { super(msg); }
    public LsfRetryableException(String msg, Throwable cause) { super("RETRYABLE", msg, cause); }
}
