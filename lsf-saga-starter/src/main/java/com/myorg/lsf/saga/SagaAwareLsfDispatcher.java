package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;

public class SagaAwareLsfDispatcher implements LsfDispatcher {

    private final LsfDispatcher delegate;
    private final LsfSagaOrchestrator orchestrator;
    private final boolean consumeMatchingEvents;

    public SagaAwareLsfDispatcher(
            LsfDispatcher delegate,
            LsfSagaOrchestrator orchestrator,
            boolean consumeMatchingEvents
    ) {
        this.delegate = delegate;
        this.orchestrator = orchestrator;
        this.consumeMatchingEvents = consumeMatchingEvents;
    }

    @Override
    public void dispatch(EventEnvelope env) {
        boolean consumed = orchestrator.onEvent(env);
        if (consumed && consumeMatchingEvents) {
            return;
        }
        delegate.dispatch(env);
    }
}
