package com.myorg.lsf.saga;

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySagaInstanceRepository implements SagaInstanceRepository {

    private final ConcurrentHashMap<String, SagaInstance> store = new ConcurrentHashMap<>();

    @Override
    public SagaInstance create(SagaInstance instance) {
        SagaInstance copy = instance.copy();
        copy.setVersion(0L);
        SagaInstance previous = store.putIfAbsent(copy.getSagaId(), copy);
        if (previous != null) {
            throw new IllegalStateException("Saga instance already exists: " + copy.getSagaId());
        }
        return copy.copy();
    }

    @Override
    public synchronized SagaInstance save(SagaInstance instance) {
        SagaInstance current = store.get(instance.getSagaId());
        if (current == null) {
            throw new IllegalStateException("Saga instance not found: " + instance.getSagaId());
        }
        if (!Objects.equals(current.getVersion(), instance.getVersion())) {
            throw new OptimisticLockingFailureException("Stale saga version for " + instance.getSagaId());
        }

        SagaInstance next = instance.copy();
        next.setVersion(instance.getVersion() + 1);
        store.put(next.getSagaId(), next);
        return next.copy();
    }

    @Override
    public Optional<SagaInstance> findById(String sagaId) {
        SagaInstance instance = store.get(sagaId);
        return instance == null ? Optional.empty() : Optional.of(instance.copy());
    }

    @Override
    public Optional<SagaInstance> findActiveByCorrelationId(String correlationId) {
        return store.values().stream()
                .filter(instance -> correlationId.equals(instance.getCorrelationId()))
                .filter(instance -> !instance.getStatus().isTerminal())
                .sorted(Comparator.comparingLong((SagaInstance instance) -> safeUpdatedAt(instance.getUpdatedAtMs())).reversed())
                .map(SagaInstance::copy)
                .findFirst();
    }

    @Override
    public List<SagaInstance> findDueTimeouts(long nowMs, int limit) {
        return store.values().stream()
                .filter(instance -> !instance.getStatus().isTerminal())
                .filter(instance -> instance.getNextTimeoutAtMs() != null && instance.getNextTimeoutAtMs() <= nowMs)
                .sorted(Comparator.comparingLong(instance -> safeUpdatedAt(instance.getNextTimeoutAtMs())))
                .limit(Math.max(1, limit))
                .map(SagaInstance::copy)
                .toList();
    }

    private long safeUpdatedAt(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }
}
