package com.myorg.lsf.saga;

import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository {

    SagaInstance create(SagaInstance instance);

    SagaInstance save(SagaInstance instance);

    Optional<SagaInstance> findById(String sagaId);

    Optional<SagaInstance> findActiveByCorrelationId(String correlationId);

    List<SagaInstance> findDueTimeouts(long nowMs, int limit);
}
