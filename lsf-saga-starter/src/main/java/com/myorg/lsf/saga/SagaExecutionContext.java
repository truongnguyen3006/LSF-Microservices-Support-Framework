package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

public record SagaExecutionContext<S>(
        String sagaId,
        String definitionName,
        String stepName,
        String correlationId,
        String requestId,
        String causationId,
        S state,
        EventEnvelope triggerEvent
) {
    public SagaCommand command(String topic, String key, String eventType, Object payload) {
        return new SagaCommand(topic, key, eventType, sagaId, payload);
    }

    public SagaCommand command(String topic, String key, String eventType, String aggregateId, Object payload) {
        return new SagaCommand(topic, key, eventType, aggregateId, payload);
    }
}
