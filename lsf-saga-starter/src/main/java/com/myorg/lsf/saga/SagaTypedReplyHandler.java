package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

@FunctionalInterface
public interface SagaTypedReplyHandler<S, P> {

    SagaReplyDecision<S> handle(SagaExecutionContext<S> context, EventEnvelope envelope, P payload);
}
