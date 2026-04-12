package com.myorg.lsf.saga;

public enum SagaStatus {
    RUNNING,
    WAITING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED,
    TIMED_OUT,
    COMPENSATION_FAILED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, COMPENSATED, FAILED, TIMED_OUT, COMPENSATION_FAILED -> true;
            case RUNNING, WAITING, COMPENSATING -> false;
        };
    }
}
