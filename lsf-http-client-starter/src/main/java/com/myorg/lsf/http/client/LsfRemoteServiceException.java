package com.myorg.lsf.http.client;

import com.myorg.lsf.contracts.core.exception.LsfRetryAware;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;

public class LsfRemoteServiceException extends RuntimeException implements LsfRetryAware {

    private final String serviceId;
    private final Integer status;
    private final String code;
    private final boolean retryable;
    private final LsfErrorResponse errorResponse;

    public LsfRemoteServiceException(
            String serviceId,
            Integer status,
            String code,
            boolean retryable,
            String message,
            Throwable cause,
            LsfErrorResponse errorResponse
    ) {
        super(message, cause);
        this.serviceId = serviceId;
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.errorResponse = errorResponse;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Integer getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public LsfErrorResponse getErrorResponse() {
        return errorResponse;
    }

    @Override
    public boolean isRetryable() {
        return retryable;
    }
}
