package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SagaStep<S> {

    private final String name;
    private final SagaCommandFactory<S> commandFactory;
    private final Duration timeout;
    private final SagaFailureMode failureMode;
    private final Map<String, ReplyHandler<S>> replyHandlers;
    private final SagaCompensation<S> compensation;

    private SagaStep(
            String name,
            SagaCommandFactory<S> commandFactory,
            Duration timeout,
            SagaFailureMode failureMode,
            Map<String, ReplyHandler<S>> replyHandlers,
            SagaCompensation<S> compensation
    ) {
        this.name = name;
        this.commandFactory = commandFactory;
        this.timeout = timeout;
        this.failureMode = failureMode;
        this.replyHandlers = Map.copyOf(replyHandlers);
        this.compensation = compensation;
    }

    public String name() {
        return name;
    }

    public SagaCommandFactory<S> commandFactory() {
        return commandFactory;
    }

    public Duration timeout() {
        return timeout;
    }

    public SagaFailureMode failureMode() {
        return failureMode;
    }

    public SagaCompensation<S> compensation() {
        return compensation;
    }

    public SagaReplyDecision<S> handleReply(
            SagaExecutionContext<S> context,
            EventEnvelope envelope,
            ObjectMapper mapper
    ) {
        ReplyHandler<S> handler = replyHandlers.get(envelope.getEventType());
        if (handler == null) {
            return null;
        }
        return handler.handle(context, envelope, mapper);
    }

    public static <S> Builder<S> builder(String name) {
        return new Builder<>(name);
    }

    static <S, P> ReplyHandler<S> wrapHandler(
            Class<P> payloadType,
            SagaTypedReplyHandler<S, P> handler
    ) {
        return (context, envelope, mapper) -> {
            try {
                P payload = mapper.treeToValue(envelope.getPayload(), payloadType);
                return handler.handle(context, envelope, payload);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Failed to convert saga reply payload for eventType=" + envelope.getEventType(),
                        ex
                );
            }
        };
    }

    public static final class Builder<S> {
        private final String name;
        private SagaCommandFactory<S> commandFactory;
        private Duration timeout;
        private SagaFailureMode failureMode = SagaFailureMode.COMPENSATE;
        private final Map<String, ReplyHandler<S>> replyHandlers = new LinkedHashMap<>();
        private SagaCompensation<S> compensation;

        private Builder(String name) {
            Assert.isTrue(StringUtils.hasText(name), "step name must not be blank");
            this.name = name;
        }

        public Builder<S> command(SagaCommandFactory<S> commandFactory) {
            this.commandFactory = commandFactory;
            return this;
        }

        public Builder<S> timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder<S> failureMode(SagaFailureMode failureMode) {
            this.failureMode = failureMode;
            return this;
        }

        public <P> Builder<S> onReply(
                String eventType,
                Class<P> payloadType,
                SagaTypedReplyHandler<S, P> handler
        ) {
            Assert.isTrue(StringUtils.hasText(eventType), "eventType must not be blank");
            Assert.notNull(payloadType, "payloadType must not be null");
            Assert.notNull(handler, "handler must not be null");
            replyHandlers.put(eventType, wrapHandler(payloadType, handler));
            return this;
        }

        public Builder<S> compensation(Consumer<SagaCompensation.Builder<S>> customizer) {
            SagaCompensation.Builder<S> builder = SagaCompensation.builder();
            customizer.accept(builder);
            this.compensation = builder.build();
            return this;
        }

        public SagaStep<S> build() {
            Assert.notNull(commandFactory, "step command must not be null");
            Assert.notEmpty(replyHandlers, "step replyHandlers must not be empty");
            Assert.notNull(failureMode, "failureMode must not be null");
            return new SagaStep<>(name, commandFactory, timeout, failureMode, replyHandlers, compensation);
        }
    }

    @FunctionalInterface
    interface ReplyHandler<S> {
        SagaReplyDecision<S> handle(
                SagaExecutionContext<S> context,
                EventEnvelope envelope,
                ObjectMapper mapper
        );
    }
}
