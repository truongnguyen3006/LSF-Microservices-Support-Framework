package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

public interface SagaEventPublisher {

    boolean isTransactional();

    void publish(String topic, String key, EventEnvelope envelope);
}
