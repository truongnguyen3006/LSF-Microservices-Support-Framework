package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLsfSagaOrchestratorTest {

    @AfterEach
    void tearDown() {
        LsfTraceContextHolder.clear();
    }

    @Test
    void shouldHandleFailureCompensationAndIdentifierPropagation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        RecordingSagaEventPublisher publisher = new RecordingSagaEventPublisher();
        DefaultLsfSagaOrchestrator orchestrator = new DefaultLsfSagaOrchestrator(
                new SagaDefinitionRegistry(List.of(testDefinition())),
                new InMemorySagaInstanceRepository(),
                publisher,
                new ObjectMapper(),
                clock,
                new LsfSagaProperties(),
                "orchestrator-service",
                null
        );

        SagaInstance started = orchestrator.start(
                "order-orchestrator",
                "saga-001",
                new OrderSagaState("ORD-001", false, false, false, "created"),
                SagaStartOptions.builder()
                        .correlationId("corr-001")
                        .requestId("req-001")
                        .build()
        );

        assertThat(started.getStatus()).isEqualTo(SagaStatus.WAITING);
        assertThat(started.getSteps().getFirst().getStatus()).isEqualTo(SagaStepStatus.DISPATCHED);
        assertThat(publisher.messages).hasSize(1);

        PublishedMessage reserveCommand = publisher.messages.getFirst();
        assertThat(reserveCommand.topic()).isEqualTo("inventory-commands");
        assertThat(reserveCommand.envelope().getCorrelationId()).isEqualTo("corr-001");
        assertThat(reserveCommand.envelope().getRequestId()).isEqualTo("req-001");
        assertThat(reserveCommand.envelope().getCausationId()).isNull();

        EventEnvelope inventoryReserved = replyEnvelope(
                "evt-inventory-reserved",
                "inventory.reserved.v1",
                "corr-001",
                reserveCommand.envelope().getEventId(),
                "req-001",
                new InventoryReservedReply("ORD-001")
        );
        assertThat(orchestrator.onEvent(inventoryReserved)).isTrue();

        assertThat(publisher.messages).hasSize(2);
        PublishedMessage paymentCommand = publisher.messages.get(1);
        assertThat(paymentCommand.topic()).isEqualTo("payment-commands");
        assertThat(paymentCommand.envelope().getCausationId()).isEqualTo("evt-inventory-reserved");
        assertThat(paymentCommand.envelope().getCorrelationId()).isEqualTo("corr-001");
        assertThat(paymentCommand.envelope().getRequestId()).isEqualTo("req-001");

        EventEnvelope paymentFailed = replyEnvelope(
                "evt-payment-failed",
                "payment.charge.failed.v1",
                "corr-001",
                paymentCommand.envelope().getEventId(),
                "req-001",
                new PaymentFailedReply("ORD-001", "card_declined")
        );
        assertThat(orchestrator.onEvent(paymentFailed)).isTrue();

        assertThat(publisher.messages).hasSize(3);
        PublishedMessage compensation = publisher.messages.get(2);
        assertThat(compensation.topic()).isEqualTo("inventory-commands");
        assertThat(compensation.envelope().getEventType()).isEqualTo("inventory.release.requested.v1");
        assertThat(compensation.envelope().getCausationId()).isEqualTo("evt-payment-failed");
        assertThat(compensation.envelope().getRequestId()).isEqualTo("req-001");

        EventEnvelope inventoryReleased = replyEnvelope(
                "evt-inventory-released",
                "inventory.released.v1",
                "corr-001",
                compensation.envelope().getEventId(),
                "req-001",
                new InventoryReleasedReply("ORD-001")
        );
        assertThat(orchestrator.onEvent(inventoryReleased)).isTrue();

        SagaInstance snapshot = orchestrator.findById("saga-001").orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(snapshot.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPENSATED);
        assertThat(snapshot.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.FAILED);
        assertThat(snapshot.getFailureReason()).contains("card_declined");
    }

    @Test
    void shouldTriggerTimeoutAndCompensatePreviousSteps() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        RecordingSagaEventPublisher publisher = new RecordingSagaEventPublisher();
        DefaultLsfSagaOrchestrator orchestrator = new DefaultLsfSagaOrchestrator(
                new SagaDefinitionRegistry(List.of(testDefinition())),
                new InMemorySagaInstanceRepository(),
                publisher,
                new ObjectMapper(),
                clock,
                new LsfSagaProperties(),
                "orchestrator-service",
                null
        );

        orchestrator.start(
                "order-orchestrator",
                "saga-timeout",
                new OrderSagaState("ORD-002", false, false, false, "created"),
                SagaStartOptions.builder().requestId("req-timeout").build()
        );

        PublishedMessage reserveCommand = publisher.messages.getFirst();
        orchestrator.onEvent(replyEnvelope(
                "evt-inventory-reserved-timeout",
                "inventory.reserved.v1",
                "saga-timeout",
                reserveCommand.envelope().getEventId(),
                "req-timeout",
                new InventoryReservedReply("ORD-002")
        ));

        PublishedMessage paymentCommand = publisher.messages.get(1);
        assertThat(paymentCommand.envelope().getEventType()).isEqualTo("payment.charge.requested.v1");

        clock.plus(Duration.ofSeconds(45));
        assertThat(orchestrator.triggerTimeouts()).isEqualTo(1);
        assertThat(publisher.messages).hasSize(3);

        SagaInstance timingOut = orchestrator.findById("saga-timeout").orElseThrow();
        assertThat(timingOut.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(timingOut.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.TIMED_OUT);
        assertThat(timingOut.getFailureReason()).contains("timed out");

        PublishedMessage compensation = publisher.messages.get(2);
        orchestrator.onEvent(replyEnvelope(
                "evt-inventory-released-timeout",
                "inventory.released.v1",
                "saga-timeout",
                compensation.envelope().getEventId(),
                "req-timeout",
                new InventoryReleasedReply("ORD-002")
        ));

        SagaInstance snapshot = orchestrator.findById("saga-timeout").orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(snapshot.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPENSATED);
        assertThat(snapshot.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.TIMED_OUT);
    }

    @Test
    void shouldPropagateTraceHeadersIntoSagaCommands() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        RecordingSagaEventPublisher publisher = new RecordingSagaEventPublisher();
        DefaultLsfSagaOrchestrator orchestrator = new DefaultLsfSagaOrchestrator(
                new SagaDefinitionRegistry(List.of(testDefinition())),
                new InMemorySagaInstanceRepository(),
                publisher,
                new ObjectMapper(),
                clock,
                new LsfSagaProperties(),
                "orchestrator-service",
                null
        );
        LsfTraceContextHolder.setContext(new LsfTraceContext(java.util.Map.of(
                CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )));

        orchestrator.start(
                "order-orchestrator",
                "saga-trace",
                new OrderSagaState("ORD-TRACE", false, false, false, "created"),
                SagaStartOptions.builder().correlationId("corr-trace").requestId("req-trace").build()
        );

        PublishedMessage firstCommand = publisher.messages.getFirst();
        assertThat(firstCommand.envelope().getTraceHeaders())
                .containsEntry(CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
    }

    @Test
    void shouldIgnoreLateReplyAfterSagaAlreadyCompleted() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        RecordingSagaEventPublisher publisher = new RecordingSagaEventPublisher();
        DefaultLsfSagaOrchestrator orchestrator = new DefaultLsfSagaOrchestrator(
                new SagaDefinitionRegistry(List.of(testDefinition())),
                new InMemorySagaInstanceRepository(),
                publisher,
                new ObjectMapper(),
                clock,
                new LsfSagaProperties(),
                "orchestrator-service",
                null
        );

        orchestrator.start(
                "order-orchestrator",
                "saga-late-reply",
                new OrderSagaState("ORD-LATE", false, false, false, "created"),
                SagaStartOptions.builder()
                        .correlationId("corr-late")
                        .requestId("req-late")
                        .build()
        );

        PublishedMessage reserveCommand = publisher.messages.getFirst();
        assertThat(orchestrator.onEvent(replyEnvelope(
                "evt-late-inventory-reserved",
                "inventory.reserved.v1",
                "corr-late",
                reserveCommand.envelope().getEventId(),
                "req-late",
                new InventoryReservedReply("ORD-LATE")
        ))).isTrue();

        PublishedMessage paymentCommand = publisher.messages.get(1);
        assertThat(orchestrator.onEvent(replyEnvelope(
                "evt-late-payment-charged",
                "payment.charged.v1",
                "corr-late",
                paymentCommand.envelope().getEventId(),
                "req-late",
                new PaymentChargedReply("ORD-LATE")
        ))).isTrue();

        SagaInstance completed = orchestrator.findById("saga-late-reply").orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(orchestrator.onEvent(replyEnvelope(
                "evt-late-duplicate",
                "payment.charged.v1",
                "corr-late",
                paymentCommand.envelope().getEventId(),
                "req-late",
                new PaymentChargedReply("ORD-LATE")
        ))).isFalse();

        SagaInstance snapshot = orchestrator.findById("saga-late-reply").orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(snapshot.getLastEventId()).isEqualTo("evt-late-payment-charged");
        assertThat(publisher.messages).hasSize(2);
    }

    private static SagaDefinition<OrderSagaState> testDefinition() {
        return SagaDefinition.<OrderSagaState>builder("order-orchestrator", OrderSagaState.class)
                .step(SagaStep.<OrderSagaState>builder("reserveInventory")
                        .command(context -> context.command(
                                "inventory-commands",
                                context.state().orderId(),
                                "inventory.reserve.requested.v1",
                                new ReserveInventoryCommand(context.state().orderId())
                        ))
                        .onReply("inventory.reserved.v1", InventoryReservedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.success(
                                        context.state().withInventoryReserved(),
                                        "inventory reserved"
                                ))
                        .onReply("inventory.reserve.failed.v1", InventoryRejectedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.failure(
                                        context.state().withLastMessage(payload.reason()),
                                        payload.reason()
                                ))
                        .compensation(compensation -> compensation
                                .command(context -> context.command(
                                        "inventory-commands",
                                        context.state().orderId(),
                                        "inventory.release.requested.v1",
                                        new ReleaseInventoryCommand(context.state().orderId())
                                ))
                                .onReply("inventory.released.v1", InventoryReleasedReply.class,
                                        (context, envelope, payload) -> SagaReplyDecision.success(
                                                context.state().withInventoryReleased(),
                                                "inventory released"
                                        ))
                                .onReply("inventory.release.failed.v1", InventoryRejectedReply.class,
                                        (context, envelope, payload) -> SagaReplyDecision.failure(
                                                context.state().withLastMessage(payload.reason()),
                                                payload.reason()
                                        ))
                        )
                        .timeout(Duration.ofSeconds(30))
                        .build())
                .step(SagaStep.<OrderSagaState>builder("chargePayment")
                        .command(context -> context.command(
                                "payment-commands",
                                context.state().orderId(),
                                "payment.charge.requested.v1",
                                new ChargePaymentCommand(context.state().orderId())
                        ))
                        .onReply("payment.charged.v1", PaymentChargedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.success(
                                        context.state().withPaymentCharged(),
                                        "payment charged"
                                ))
                        .onReply("payment.charge.failed.v1", PaymentFailedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.failure(
                                        context.state().withLastMessage(payload.reason()),
                                        payload.reason()
                                ))
                        .timeout(Duration.ofSeconds(30))
                        .failureMode(SagaFailureMode.COMPENSATE)
                        .build())
                .build();
    }

    private static EventEnvelope replyEnvelope(
            String eventId,
            String eventType,
            String correlationId,
            String causationId,
            String requestId,
            Object payload
    ) {
        ObjectMapper mapper = new ObjectMapper();
        return EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .version(1)
                .aggregateId(correlationId)
                .correlationId(correlationId)
                .causationId(causationId)
                .requestId(requestId)
                .occurredAtMs(System.currentTimeMillis())
                .producer("test-service")
                .payload(mapper.valueToTree(payload))
                .build();
    }

    private record PublishedMessage(String topic, String key, EventEnvelope envelope) {
    }

    private static final class RecordingSagaEventPublisher implements SagaEventPublisher {
        private final List<PublishedMessage> messages = new ArrayList<>();

        @Override
        public boolean isTransactional() {
            return false;
        }

        @Override
        public void publish(String topic, String key, EventEnvelope envelope) {
            messages.add(new PublishedMessage(topic, key, envelope));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void plus(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private record OrderSagaState(
            String orderId,
            boolean inventoryReserved,
            boolean paymentCharged,
            boolean inventoryReleased,
            String lastMessage
    ) {
        private OrderSagaState withInventoryReserved() {
            return new OrderSagaState(orderId, true, paymentCharged, inventoryReleased, "inventory reserved");
        }

        private OrderSagaState withPaymentCharged() {
            return new OrderSagaState(orderId, inventoryReserved, true, inventoryReleased, "payment charged");
        }

        private OrderSagaState withInventoryReleased() {
            return new OrderSagaState(orderId, inventoryReserved, paymentCharged, true, "inventory released");
        }

        private OrderSagaState withLastMessage(String message) {
            return new OrderSagaState(orderId, inventoryReserved, paymentCharged, inventoryReleased, message);
        }
    }

    private record ReserveInventoryCommand(String orderId) {
    }

    private record ReleaseInventoryCommand(String orderId) {
    }

    private record ChargePaymentCommand(String orderId) {
    }

    private record InventoryReservedReply(String orderId) {
    }

    private record InventoryReleasedReply(String orderId) {
    }

    private record InventoryRejectedReply(String orderId, String reason) {
    }

    private record PaymentChargedReply(String orderId) {
    }

    private record PaymentFailedReply(String orderId, String reason) {
    }
}
