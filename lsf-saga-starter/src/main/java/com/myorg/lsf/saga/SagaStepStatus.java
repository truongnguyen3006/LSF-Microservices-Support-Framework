package com.myorg.lsf.saga;

public enum SagaStepStatus {
    PENDING,
    DISPATCHED,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    COMPENSATING,
    COMPENSATED,
    COMPENSATION_FAILED,
    SKIPPED
}
