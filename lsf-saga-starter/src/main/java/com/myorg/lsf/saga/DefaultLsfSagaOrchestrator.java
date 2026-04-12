package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EnvelopeBuilder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfTraceHeaders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class DefaultLsfSagaOrchestrator implements LsfSagaOrchestrator {

    private final SagaDefinitionRegistry definitionRegistry;
    private final SagaInstanceRepository repository;
    private final SagaEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final LsfSagaProperties properties;
    private final String producerName;
    private final TransactionTemplate transactionTemplate;

    public DefaultLsfSagaOrchestrator(
            SagaDefinitionRegistry definitionRegistry,
            SagaInstanceRepository repository,
            SagaEventPublisher publisher,
            ObjectMapper mapper,
            Clock clock,
            LsfSagaProperties properties,
            String producerName,
            TransactionTemplate transactionTemplate
    ) {
        this.definitionRegistry = definitionRegistry;
        this.repository = repository;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
        this.properties = properties;
        this.producerName = producerName;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public SagaInstance start(String definitionName, String sagaId, Object initialState) {
        return start(definitionName, sagaId, initialState, SagaStartOptions.builder().build());
    }

    @Override
    public SagaInstance start(String definitionName, String sagaId, Object initialState, SagaStartOptions options) {
        return executeMaybeInTransaction(() -> doStart(definitionName, sagaId, initialState, options));
    }

    @Override
    public boolean onEvent(EventEnvelope envelope) {
        return executeMaybeInTransaction(() -> doOnEvent(envelope));
    }

    @Override
    public Optional<SagaInstance> findById(String sagaId) {
        return repository.findById(sagaId);
    }

    @Override
    public int triggerTimeouts() {
        int processed = 0;
        List<SagaInstance> due = repository.findDueTimeouts(clock.millis(), properties.getTimeoutScanner().getBatchSize());
        for (SagaInstance instance : due) {
            Boolean handled = executeMaybeInTransaction(() -> doProcessTimeout(instance.getSagaId()));
            if (Boolean.TRUE.equals(handled)) {
                processed++;
            }
        }
        return processed;
    }

    private SagaInstance doStart(
            String definitionName,
            String sagaId,
            Object initialState,
            SagaStartOptions options
    ) {
        if (!StringUtils.hasText(sagaId)) {
            throw new IllegalArgumentException("sagaId must not be blank");
        }
        if (repository.findById(sagaId).isPresent()) {
            throw new IllegalStateException("Saga instance already exists: " + sagaId);
        }

        SagaDefinition<Object> definition = definitionRegistry.getRequired(definitionName);
        Object typedState = mapper.convertValue(initialState, definition.stateType());
        long now = clock.millis();

        SagaInstance instance = new SagaInstance();
        instance.setSagaId(sagaId);
        instance.setDefinitionName(definition.name());
        instance.setStatus(SagaStatus.RUNNING);
        instance.setPhase(SagaPhase.FORWARD);
        instance.setCurrentStepIndex(0);
        instance.setCompensationStepIndex(null);
        instance.setCurrentStep(definition.stepAt(0).name());
        instance.setCorrelationId(resolveCorrelationId(sagaId, options));
        instance.setRequestId(resolveRequestId(instance.getCorrelationId(), options));
        instance.setCausationId(options == null ? null : options.getCausationId());
        instance.setStateData(mapper.valueToTree(typedState));
        instance.setSteps(initialSteps(definition));
        instance.setCreatedAtMs(now);
        instance.setUpdatedAtMs(now);
        instance.setVersion(0L);

        SagaInstance created = repository.create(instance);
        return dispatchForwardStep(definition, created, null, true);
    }

    private boolean doOnEvent(EventEnvelope envelope) {
        if (envelope == null) {
            return false;
        }

        Optional<SagaInstance> located = locateActiveSaga(envelope);
        if (located.isEmpty()) {
            return false;
        }

        SagaInstance instance = located.get();
        if (instance.getStatus().isTerminal()) {
            return false;
        }

        if (instance.getPhase() == SagaPhase.COMPENSATION) {
            return handleCompensationReply(instance, envelope);
        }
        return handleForwardReply(instance, envelope);
    }

    private boolean doProcessTimeout(String sagaId) {
        Optional<SagaInstance> located = repository.findById(sagaId);
        if (located.isEmpty()) {
            return false;
        }

        SagaInstance instance = located.get();
        if (instance.getStatus().isTerminal() || instance.getNextTimeoutAtMs() == null || instance.getNextTimeoutAtMs() > clock.millis()) {
            return false;
        }

        if (instance.getPhase() == SagaPhase.COMPENSATION) {
            return handleCompensationTimeout(instance);
        }
        return handleForwardTimeout(instance);
    }

    private boolean handleForwardReply(SagaInstance instance, EventEnvelope envelope) {
        SagaDefinition<Object> definition = definitionRegistry.getRequired(instance.getDefinitionName());
        SagaStep<Object> step = definition.stepAt(instance.getCurrentStepIndex());
        Object state = readState(instance, definition);
        SagaExecutionContext<Object> context = executionContext(instance, step.name(), state, envelope);
        SagaReplyDecision<Object> decision = step.handleReply(context, envelope, mapper);
        if (decision == null) {
            return false;
        }

        long now = clock.millis();
        SagaInstanceStep stepState = instance.getSteps().get(instance.getCurrentStepIndex());
        stepState.setReplyEventId(envelope.getEventId());
        stepState.setTimeoutAtMs(null);
        stepState.setFailureReason(null);
        writeState(instance, decision.state());
        instance.setLastEventId(envelope.getEventId());
        instance.setCausationId(envelope.getEventId());
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);

        if (decision.outcome() == SagaReplyOutcome.SUCCESS) {
            stepState.setStatus(SagaStepStatus.COMPLETED);
            stepState.setCompletedAtMs(now);
            if (instance.getCurrentStepIndex() >= definition.steps().size() - 1) {
                instance.setStatus(SagaStatus.COMPLETED);
                instance.setCurrentStep(null);
                repository.save(instance);
                return true;
            }

            instance.setStatus(SagaStatus.RUNNING);
            instance.setCurrentStepIndex(instance.getCurrentStepIndex() + 1);
            instance.setCurrentStep(definition.stepAt(instance.getCurrentStepIndex()).name());
            SagaInstance saved = repository.save(instance);
            dispatchForwardStep(definition, saved, envelope, false);
            return true;
        }

        stepState.setStatus(SagaStepStatus.FAILED);
        stepState.setFailureReason(messageOrFallback(decision.message(), "Saga step failed on reply " + envelope.getEventType()));
        instance.setFailureReason(stepState.getFailureReason());
        SagaInstance saved = repository.save(instance);

        if (step.failureMode() == SagaFailureMode.COMPENSATE) {
            initiateCompensation(saved, definition, envelope, SagaStatus.FAILED, false);
            return true;
        }

        saved.setStatus(SagaStatus.FAILED);
        saved.setCurrentStep(null);
        saved.setNextTimeoutAtMs(null);
        repository.save(saved);
        return true;
    }

    private boolean handleCompensationReply(SagaInstance instance, EventEnvelope envelope) {
        SagaDefinition<Object> definition = definitionRegistry.getRequired(instance.getDefinitionName());
        int compensationIndex = instance.getCompensationStepIndex();
        SagaStep<Object> step = definition.stepAt(compensationIndex);
        SagaCompensation<Object> compensation = step.compensation();
        if (compensation == null) {
            return false;
        }

        Object state = readState(instance, definition);
        SagaExecutionContext<Object> context = executionContext(instance, step.name(), state, envelope);
        SagaReplyDecision<Object> decision = compensation.handleReply(context, envelope, mapper);
        if (decision == null) {
            return false;
        }

        long now = clock.millis();
        SagaInstanceStep stepState = instance.getSteps().get(compensationIndex);
        stepState.setCompensationReplyEventId(envelope.getEventId());
        stepState.setCompensationTimeoutAtMs(null);
        stepState.setCompensationFailureReason(null);
        writeState(instance, decision.state());
        instance.setLastEventId(envelope.getEventId());
        instance.setCausationId(envelope.getEventId());
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);

        if (decision.outcome() == SagaReplyOutcome.SUCCESS) {
            stepState.setStatus(SagaStepStatus.COMPENSATED);
            stepState.setCompensationCompletedAtMs(now);

            int nextIndex = findPreviousCompensableStep(instance, definition, compensationIndex - 1);
            if (nextIndex < 0) {
                instance.setStatus(SagaStatus.COMPENSATED);
                instance.setCurrentStep(null);
                instance.setCompensationStepIndex(null);
                repository.save(instance);
                return true;
            }

            instance.setStatus(SagaStatus.COMPENSATING);
            instance.setCompensationStepIndex(nextIndex);
            instance.setCurrentStep(definition.stepAt(nextIndex).name());
            SagaInstance saved = repository.save(instance);
            dispatchCompensation(definition, saved, nextIndex, envelope, false);
            return true;
        }

        stepState.setStatus(SagaStepStatus.COMPENSATION_FAILED);
        stepState.setCompensationFailureReason(
                messageOrFallback(decision.message(), "Compensation failed on reply " + envelope.getEventType())
        );
        instance.setStatus(SagaStatus.COMPENSATION_FAILED);
        instance.setFailureReason(stepState.getCompensationFailureReason());
        instance.setCurrentStep(step.name());
        repository.save(instance);
        return true;
    }

    private boolean handleForwardTimeout(SagaInstance instance) {
        SagaDefinition<Object> definition = definitionRegistry.getRequired(instance.getDefinitionName());
        SagaStep<Object> step = definition.stepAt(instance.getCurrentStepIndex());
        SagaInstanceStep stepState = instance.getSteps().get(instance.getCurrentStepIndex());
        if (stepState.getStatus() != SagaStepStatus.DISPATCHED) {
            return false;
        }

        String reason = "Saga step timed out: " + step.name();
        long now = clock.millis();
        stepState.setStatus(SagaStepStatus.TIMED_OUT);
        stepState.setTimeoutAtMs(null);
        stepState.setFailureReason(reason);
        instance.setFailureReason(reason);
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);

        SagaInstance saved = repository.save(instance);
        if (step.failureMode() == SagaFailureMode.COMPENSATE) {
            initiateCompensation(saved, definition, null, SagaStatus.TIMED_OUT, false);
            return true;
        }

        saved.setStatus(SagaStatus.TIMED_OUT);
        saved.setCurrentStep(step.name());
        repository.save(saved);
        return true;
    }

    private boolean handleCompensationTimeout(SagaInstance instance) {
        SagaDefinition<Object> definition = definitionRegistry.getRequired(instance.getDefinitionName());
        int compensationIndex = instance.getCompensationStepIndex();
        SagaStep<Object> step = definition.stepAt(compensationIndex);
        SagaInstanceStep stepState = instance.getSteps().get(compensationIndex);
        if (stepState.getStatus() != SagaStepStatus.COMPENSATING) {
            return false;
        }

        String reason = "Saga compensation timed out: " + step.name();
        long now = clock.millis();
        stepState.setStatus(SagaStepStatus.COMPENSATION_FAILED);
        stepState.setCompensationFailureReason(reason);
        stepState.setCompensationTimeoutAtMs(null);
        instance.setStatus(SagaStatus.COMPENSATION_FAILED);
        instance.setFailureReason(reason);
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);
        repository.save(instance);
        return true;
    }

    private void initiateCompensation(
            SagaInstance instance,
            SagaDefinition<Object> definition,
            EventEnvelope trigger,
            SagaStatus terminalStatusIfNone,
            boolean propagateFailure
    ) {
        int compensationIndex = findPreviousCompensableStep(instance, definition, instance.getCurrentStepIndex() - 1);
        if (compensationIndex < 0) {
            instance.setStatus(terminalStatusIfNone);
            instance.setCurrentStep(null);
            instance.setCompensationStepIndex(null);
            instance.setNextTimeoutAtMs(null);
            repository.save(instance);
            return;
        }

        instance.setPhase(SagaPhase.COMPENSATION);
        instance.setStatus(SagaStatus.COMPENSATING);
        instance.setCompensationStepIndex(compensationIndex);
        instance.setCurrentStep(definition.stepAt(compensationIndex).name());
        SagaInstance saved = repository.save(instance);
        dispatchCompensation(definition, saved, compensationIndex, trigger, propagateFailure);
    }

    private SagaInstance dispatchForwardStep(
            SagaDefinition<Object> definition,
            SagaInstance instance,
            EventEnvelope trigger,
            boolean propagateFailure
    ) {
        SagaStep<Object> step = definition.stepAt(instance.getCurrentStepIndex());
        Object state = readState(instance, definition);
        SagaExecutionContext<Object> context = executionContext(instance, step.name(), state, trigger);
        SagaCommand command = requireCommand(step.commandFactory().create(context), step.name());
        EventEnvelope envelope = EnvelopeBuilder.wrap(
                mapper,
                command.eventType(),
                1,
                command.effectiveAggregateId(instance.getSagaId()),
                instance.getCorrelationId(),
                resolveCausationId(instance, trigger),
                instance.getRequestId(),
                producerName,
                command.payload()
        );
        LsfTraceHeaders.enrichEnvelope(envelope);

        try {
            publisher.publish(command.topic(), command.effectiveKey(instance.getSagaId()), envelope);
            long now = clock.millis();
            SagaInstanceStep stepState = instance.getSteps().get(instance.getCurrentStepIndex());
            stepState.setStatus(SagaStepStatus.DISPATCHED);
            if (stepState.getStartedAtMs() == null) {
                stepState.setStartedAtMs(now);
            }
            stepState.setCommandEventId(envelope.getEventId());
            stepState.setTimeoutAtMs(now + resolveStepTimeout(step).toMillis());
            stepState.setFailureReason(null);

            instance.setPhase(SagaPhase.FORWARD);
            instance.setStatus(SagaStatus.WAITING);
            instance.setCurrentStep(step.name());
            instance.setLastEventId(envelope.getEventId());
            instance.setCausationId(envelope.getEventId());
            instance.setUpdatedAtMs(now);
            instance.setNextTimeoutAtMs(stepState.getTimeoutAtMs());
            return repository.save(instance);
        } catch (RuntimeException ex) {
            handleForwardDispatchFailure(instance, step, ex);
            if (propagateFailure) {
                throw ex;
            }
            return instance;
        }
    }

    private void dispatchCompensation(
            SagaDefinition<Object> definition,
            SagaInstance instance,
            int compensationIndex,
            EventEnvelope trigger,
            boolean propagateFailure
    ) {
        SagaStep<Object> step = definition.stepAt(compensationIndex);
        SagaCompensation<Object> compensation = step.compensation();
        if (compensation == null) {
            throw new IllegalStateException("No compensation configured for step " + step.name());
        }

        Object state = readState(instance, definition);
        SagaExecutionContext<Object> context = executionContext(instance, step.name(), state, trigger);
        SagaCommand command = requireCommand(compensation.commandFactory().create(context), step.name());
        EventEnvelope envelope = EnvelopeBuilder.wrap(
                mapper,
                command.eventType(),
                1,
                command.effectiveAggregateId(instance.getSagaId()),
                instance.getCorrelationId(),
                resolveCausationId(instance, trigger),
                instance.getRequestId(),
                producerName,
                command.payload()
        );
        LsfTraceHeaders.enrichEnvelope(envelope);

        try {
            publisher.publish(command.topic(), command.effectiveKey(instance.getSagaId()), envelope);
            long now = clock.millis();
            SagaInstanceStep stepState = instance.getSteps().get(compensationIndex);
            stepState.setStatus(SagaStepStatus.COMPENSATING);
            stepState.setCompensationStartedAtMs(now);
            stepState.setCompensationCommandEventId(envelope.getEventId());
            stepState.setCompensationTimeoutAtMs(now + resolveCompensationTimeout(step).toMillis());
            stepState.setCompensationFailureReason(null);

            instance.setPhase(SagaPhase.COMPENSATION);
            instance.setStatus(SagaStatus.COMPENSATING);
            instance.setCurrentStep(step.name());
            instance.setLastEventId(envelope.getEventId());
            instance.setCausationId(envelope.getEventId());
            instance.setUpdatedAtMs(now);
            instance.setNextTimeoutAtMs(stepState.getCompensationTimeoutAtMs());
            repository.save(instance);
        } catch (RuntimeException ex) {
            handleCompensationDispatchFailure(instance, compensationIndex, ex);
            if (propagateFailure) {
                throw ex;
            }
        }
    }

    private void handleForwardDispatchFailure(SagaInstance instance, SagaStep<?> step, RuntimeException ex) {
        String reason = "Failed to dispatch saga step " + step.name() + ": " + rootMessage(ex);
        long now = clock.millis();
        SagaInstanceStep stepState = instance.getSteps().get(instance.getCurrentStepIndex());
        stepState.setStatus(SagaStepStatus.FAILED);
        stepState.setFailureReason(reason);
        stepState.setTimeoutAtMs(null);

        instance.setStatus(SagaStatus.FAILED);
        instance.setFailureReason(reason);
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);
        repository.save(instance);
    }

    private void handleCompensationDispatchFailure(SagaInstance instance, int compensationIndex, RuntimeException ex) {
        String reason = "Failed to dispatch saga compensation: " + rootMessage(ex);
        long now = clock.millis();
        SagaInstanceStep stepState = instance.getSteps().get(compensationIndex);
        stepState.setStatus(SagaStepStatus.COMPENSATION_FAILED);
        stepState.setCompensationFailureReason(reason);
        stepState.setCompensationTimeoutAtMs(null);

        instance.setStatus(SagaStatus.COMPENSATION_FAILED);
        instance.setFailureReason(reason);
        instance.setUpdatedAtMs(now);
        instance.setNextTimeoutAtMs(null);
        repository.save(instance);
    }

    private Optional<SagaInstance> locateActiveSaga(EventEnvelope envelope) {
        if (StringUtils.hasText(envelope.getCorrelationId())) {
            Optional<SagaInstance> byCorrelation = repository.findActiveByCorrelationId(envelope.getCorrelationId());
            if (byCorrelation.isPresent()) {
                return byCorrelation;
            }
        }
        if (StringUtils.hasText(envelope.getAggregateId())) {
            return repository.findById(envelope.getAggregateId())
                    .filter(instance -> !instance.getStatus().isTerminal());
        }
        return Optional.empty();
    }

    private int findPreviousCompensableStep(
            SagaInstance instance,
            SagaDefinition<Object> definition,
            int fromIndex
    ) {
        for (int i = fromIndex; i >= 0; i--) {
            SagaInstanceStep stepState = instance.getSteps().get(i);
            SagaStep<Object> step = definition.stepAt(i);
            if (stepState.getStatus() == SagaStepStatus.COMPLETED && step.compensation() != null) {
                return i;
            }
        }
        return -1;
    }

    private Object readState(SagaInstance instance, SagaDefinition<Object> definition) {
        return mapper.convertValue(instance.getStateData(), definition.stateType());
    }

    private void writeState(SagaInstance instance, Object state) {
        instance.setStateData(mapper.valueToTree(state));
    }

    private SagaExecutionContext<Object> executionContext(
            SagaInstance instance,
            String stepName,
            Object state,
            EventEnvelope trigger
    ) {
        return new SagaExecutionContext<>(
                instance.getSagaId(),
                instance.getDefinitionName(),
                stepName,
                instance.getCorrelationId(),
                instance.getRequestId(),
                resolveCausationId(instance, trigger),
                state,
                trigger
        );
    }

    private String resolveCorrelationId(String sagaId, SagaStartOptions options) {
        if (options != null && StringUtils.hasText(options.getCorrelationId())) {
            return options.getCorrelationId();
        }
        return sagaId;
    }

    private String resolveRequestId(String correlationId, SagaStartOptions options) {
        if (options != null && StringUtils.hasText(options.getRequestId())) {
            return options.getRequestId();
        }
        return correlationId;
    }

    private String resolveCausationId(SagaInstance instance, EventEnvelope trigger) {
        if (trigger != null && StringUtils.hasText(trigger.getEventId())) {
            return trigger.getEventId();
        }
        if (StringUtils.hasText(instance.getLastEventId())) {
            return instance.getLastEventId();
        }
        return instance.getCausationId();
    }

    private Duration resolveStepTimeout(SagaStep<?> step) {
        return step.timeout() != null ? step.timeout() : properties.getDefaultStepTimeout();
    }

    private Duration resolveCompensationTimeout(SagaStep<?> step) {
        SagaCompensation<?> compensation = step.compensation();
        if (compensation != null && compensation.timeout() != null) {
            return compensation.timeout();
        }
        return resolveStepTimeout(step);
    }

    private SagaCommand requireCommand(SagaCommand command, String stepName) {
        if (command == null) {
            throw new IllegalStateException("Saga step returned null command: " + stepName);
        }
        return command;
    }

    private List<SagaInstanceStep> initialSteps(SagaDefinition<Object> definition) {
        List<SagaInstanceStep> steps = new ArrayList<>();
        for (SagaStep<Object> step : definition.steps()) {
            steps.add(SagaInstanceStep.initial(step.name()));
        }
        return steps;
    }

    private String messageOrFallback(String message, String fallback) {
        return StringUtils.hasText(message) ? message : fallback;
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (StringUtils.hasText(message) ? ": " + message : "");
    }

    private <T> T executeMaybeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null && publisher.isTransactional()) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }
}
