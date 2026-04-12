package com.myorg.lsf.contracts.core.exception;

public final class LsfRetryDecisions {

    private LsfRetryDecisions() {
    }

    public static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LsfRetryAware retryAware) {
                return retryAware.isRetryable();
            }
            current = current.getCause();
        }
        return true;
    }
}
