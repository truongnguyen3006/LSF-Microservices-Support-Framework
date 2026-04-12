package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SagaCompensation<S> {

    private final SagaCommandFactory<S> commandFactory;
    private final Duration timeout;
    private final Map<String, SagaStep.ReplyHandler<S>> replyHandlers;

    private SagaCompensation(
            SagaCommandFactory<S> commandFactory,
            Duration timeout,
            Map<String, SagaStep.ReplyHandler<S>> replyHandlers
    ) {
        this.commandFactory = commandFactory;
        this.timeout = timeout;
        this.replyHandlers = Map.copyOf(replyHandlers);
    }

    public SagaCommandFactory<S> commandFactory() {
        return commandFactory;
    }

    public Duration timeout() {
        return timeout;
    }

    public SagaReplyDecision<S> handleReply(
            SagaExecutionContext<S> context,
            EventEnvelope envelope,
            ObjectMapper mapper
    ) {
        SagaStep.ReplyHandler<S> handler = replyHandlers.get(envelope.getEventType());
        if (handler == null) {
            return null;
        }
        return handler.handle(context, envelope, mapper);
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public static final class Builder<S> {
        private SagaCommandFactory<S> commandFactory;
        private Duration timeout;
        private final Map<String, SagaStep.ReplyHandler<S>> replyHandlers = new LinkedHashMap<>();

        public Builder<S> command(SagaCommandFactory<S> commandFactory) {
            this.commandFactory = commandFactory;
            return this;
        }

        public Builder<S> timeout(Duration timeout) {
            this.timeout = timeout;
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
            replyHandlers.put(eventType, SagaStep.wrapHandler(payloadType, handler));
            return this;
        }

        public SagaCompensation<S> build() {
            Assert.notNull(commandFactory, "compensation command must not be null");
            Assert.notEmpty(replyHandlers, "compensation replyHandlers must not be empty");
            return new SagaCompensation<>(commandFactory, timeout, replyHandlers);
        }
    }
}
