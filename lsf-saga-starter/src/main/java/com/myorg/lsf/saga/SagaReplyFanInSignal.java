package com.myorg.lsf.saga;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public record SagaReplyFanInSignal(
        boolean success,
        String failureReason
) {
    public SagaReplyFanInSignal {
        if (!success) {
            Assert.isTrue(StringUtils.hasText(failureReason), "failureReason must not be blank when success=false");
        }
    }

    public static SagaReplyFanInSignal successful() {
        return new SagaReplyFanInSignal(true, null);
    }

    public static SagaReplyFanInSignal failure(String failureReason) {
        return new SagaReplyFanInSignal(false, failureReason);
    }
}
