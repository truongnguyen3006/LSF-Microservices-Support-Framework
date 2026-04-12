package com.myorg.lsf.saga;

public enum SagaReplyFanInOutcome {
    IN_PROGRESS,
    COMPLETED_SUCCESS,
    COMPLETED_FAILURE,
    EXPIRED,
    IGNORED_ALREADY_COMPLETE
}
