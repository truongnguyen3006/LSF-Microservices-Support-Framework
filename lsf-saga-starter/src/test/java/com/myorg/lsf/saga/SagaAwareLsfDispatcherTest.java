package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SagaAwareLsfDispatcherTest {

    @Test
    void shouldConsumeMatchingSagaRepliesBeforeDelegateDispatcher() {
        RecordingSagaEventPublisher publisher = new RecordingSagaEventPublisher();
        DefaultLsfSagaOrchestrator orchestrator = new DefaultLsfSagaOrchestrator(
                new SagaDefinitionRegistry(List.of(simpleDefinition())),
                new InMemorySagaInstanceRepository(),
                publisher,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-06T00:00:00Z"), ZoneOffset.UTC),
                new LsfSagaProperties(),
                "dispatcher-test",
                null
        );

        orchestrator.start(
                "simple-orchestrator",
                "dispatcher-saga",
                new SimpleState("ORD-009", false),
                SagaStartOptions.builder().build()
        );

        PublishedMessage firstCommand = publisher.messages.getFirst();
        AtomicInteger delegateCalls = new AtomicInteger();
        LsfDispatcher delegate = envelope -> delegateCalls.incrementAndGet();
        SagaAwareLsfDispatcher dispatcher = new SagaAwareLsfDispatcher(delegate, orchestrator, true);

        dispatcher.dispatch(EventEnvelope.builder()
                .eventId("evt-simple-success")
                .eventType("payment.charged.v1")
                .version(1)
                .aggregateId("dispatcher-saga")
                .correlationId("dispatcher-saga")
                .causationId(firstCommand.envelope().getEventId())
                .requestId("dispatcher-saga")
                .occurredAtMs(System.currentTimeMillis())
                .producer("payment-service")
                .payload(new ObjectMapper().valueToTree(new PaymentSucceeded("ORD-009")))
                .build());

        assertThat(delegateCalls.get()).isZero();
        SagaInstance snapshot = orchestrator.findById("dispatcher-saga").orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(snapshot.getSteps().getFirst().getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
    }

    private static SagaDefinition<SimpleState> simpleDefinition() {
        return SagaDefinition.<SimpleState>builder("simple-orchestrator", SimpleState.class)
                .step(SagaStep.<SimpleState>builder("chargePayment")
                        .command(context -> context.command(
                                "payment-commands",
                                context.state().orderId(),
                                "payment.charge.requested.v1",
                                new ChargePayment(context.state().orderId())
                        ))
                        .onReply("payment.charged.v1", PaymentSucceeded.class,
                                (context, envelope, payload) -> SagaReplyDecision.success(context.state().withCharged()))
                        .timeout(Duration.ofSeconds(15))
                        .failureMode(SagaFailureMode.FAIL)
                        .build())
                .build();
    }

    private record SimpleState(String orderId, boolean charged) {
        private SimpleState withCharged() {
            return new SimpleState(orderId, true);
        }
    }

    private record ChargePayment(String orderId) {
    }

    private record PaymentSucceeded(String orderId) {
    }

    private record PublishedMessage(String topic, String key, EventEnvelope envelope) {
    }

    private static final class RecordingSagaEventPublisher implements SagaEventPublisher {
        private final List<PublishedMessage> messages = new java.util.ArrayList<>();

        @Override
        public boolean isTransactional() {
            return false;
        }

        @Override
        public void publish(String topic, String key, EventEnvelope envelope) {
            messages.add(new PublishedMessage(topic, key, envelope));
        }
    }
}
