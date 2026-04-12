package com.myorg.lsf.saga;

public record SagaReplyFanInSession(
        String aggregateId,
        int expectedReplies,
        int receivedReplies,
        boolean failed,
        String failureReason,
        String commandEventId,
        String requestId,
        long createdAtMs,
        long updatedAtMs,
        long expiresAtMs
) {
    public boolean isComplete() {
        return receivedReplies >= expectedReplies;
    }

    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0 && expiresAtMs <= nowMs;
    }

    public int pendingReplies() {
        return Math.max(0, expectedReplies - receivedReplies);
    }

    public long ageMs(long nowMs) {
        return Math.max(0L, nowMs - createdAtMs);
    }
}
