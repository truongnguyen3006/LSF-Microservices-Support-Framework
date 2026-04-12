package com.myorg.lsf.saga;

public record SagaReplyFanInUpdate(
        SagaReplyFanInOutcome outcome,
        SagaReplyFanInSession session
) {
    public boolean isTerminal() {
        return outcome == SagaReplyFanInOutcome.COMPLETED_SUCCESS
                || outcome == SagaReplyFanInOutcome.COMPLETED_FAILURE;
    }

    public boolean shouldIgnore() {
        return outcome == SagaReplyFanInOutcome.EXPIRED
                || outcome == SagaReplyFanInOutcome.IGNORED_ALREADY_COMPLETE;
    }
}
