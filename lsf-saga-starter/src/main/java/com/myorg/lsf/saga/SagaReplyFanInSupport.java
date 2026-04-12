package com.myorg.lsf.saga;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;

public final class SagaReplyFanInSupport {

    private static final String DEFAULT_FAILURE_REASON = "Downstream fan-in reply failed";

    private SagaReplyFanInSupport() {
    }

    public static SagaReplyFanInSession start(
            String aggregateId,
            int expectedReplies,
            String commandEventId,
            String requestId,
            long nowMs,
            Duration ttl
    ) {
        Assert.isTrue(StringUtils.hasText(aggregateId), "aggregateId must not be blank");
        Assert.isTrue(expectedReplies > 0, "expectedReplies must be greater than 0");
        Assert.notNull(ttl, "ttl must not be null");
        Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl must be greater than 0");

        return new SagaReplyFanInSession(
                aggregateId,
                expectedReplies,
                0,
                false,
                "",
                commandEventId,
                requestId,
                nowMs,
                nowMs,
                nowMs + ttl.toMillis()
        );
    }

    public static SagaReplyFanInUpdate apply(
            SagaReplyFanInSession session,
            SagaReplyFanInSignal signal,
            long nowMs,
            Duration ttl
    ) {
        Assert.notNull(session, "session must not be null");
        Assert.notNull(signal, "signal must not be null");
        Assert.notNull(ttl, "ttl must not be null");
        Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl must be greater than 0");

        if (session.isExpired(nowMs)) {
            return new SagaReplyFanInUpdate(SagaReplyFanInOutcome.EXPIRED, session);
        }
        if (session.isComplete()) {
            return new SagaReplyFanInUpdate(SagaReplyFanInOutcome.IGNORED_ALREADY_COMPLETE, session);
        }

        boolean failed = session.failed() || !signal.success();
        String failureReason = failed
                ? resolveFailureReason(session.failureReason(), signal.failureReason())
                : session.failureReason();
        int receivedReplies = Math.min(session.receivedReplies() + 1, session.expectedReplies());
        SagaReplyFanInSession updated = new SagaReplyFanInSession(
                session.aggregateId(),
                session.expectedReplies(),
                receivedReplies,
                failed,
                failureReason,
                session.commandEventId(),
                session.requestId(),
                session.createdAtMs(),
                nowMs,
                nowMs + ttl.toMillis()
        );

        if (!updated.isComplete()) {
            return new SagaReplyFanInUpdate(SagaReplyFanInOutcome.IN_PROGRESS, updated);
        }
        return new SagaReplyFanInUpdate(
                updated.failed() ? SagaReplyFanInOutcome.COMPLETED_FAILURE : SagaReplyFanInOutcome.COMPLETED_SUCCESS,
                updated
        );
    }

    private static String resolveFailureReason(String currentFailureReason, String newFailureReason) {
        if (StringUtils.hasText(currentFailureReason)) {
            return currentFailureReason;
        }
        if (StringUtils.hasText(newFailureReason)) {
            return newFailureReason;
        }
        return DEFAULT_FAILURE_REASON;
    }
}
