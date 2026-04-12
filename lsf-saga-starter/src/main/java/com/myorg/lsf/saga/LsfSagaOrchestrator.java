package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

import java.util.Optional;

public interface LsfSagaOrchestrator {

    SagaInstance start(String definitionName, String sagaId, Object initialState);

    SagaInstance start(String definitionName, String sagaId, Object initialState, SagaStartOptions options);

    boolean onEvent(EventEnvelope envelope);

    Optional<SagaInstance> findById(String sagaId);

    int triggerTimeouts();
}
