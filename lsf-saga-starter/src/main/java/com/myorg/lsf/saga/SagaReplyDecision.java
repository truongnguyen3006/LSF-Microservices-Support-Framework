package com.myorg.lsf.saga;

public record SagaReplyDecision<S>(
        SagaReplyOutcome outcome,
        S state,
        String message
) {
    public static <S> SagaReplyDecision<S> success(S state) {
        return new SagaReplyDecision<>(SagaReplyOutcome.SUCCESS, state, null);
    }

    public static <S> SagaReplyDecision<S> success(S state, String message) {
        return new SagaReplyDecision<>(SagaReplyOutcome.SUCCESS, state, message);
    }

    public static <S> SagaReplyDecision<S> failure(S state, String message) {
        return new SagaReplyDecision<>(SagaReplyOutcome.FAILURE, state, message);
    }
}
