package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EnvelopeBuilder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import com.myorg.lsf.eventing.LsfPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(
        classes = LsfSagaRuntimeIntegrationTest.LsfSagaRuntimeIntegrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.application.name=lsf-saga-it",
                "lsf.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "lsf.kafka.schema-registry-url=mock://lsf-saga-it",
                "lsf.kafka.consumer.group-id=lsf-saga-it-group",
                "lsf.kafka.consumer.batch=false",
                "lsf.kafka.consumer.concurrency=1",
                "lsf.kafka.consumer.auto-offset-reset=earliest",
                "lsf.kafka.consumer.json-value-type=com.myorg.lsf.contracts.core.envelope.EventEnvelope",
                "lsf.kafka.dlq.enabled=false",
                "lsf.eventing.producer-name=checkout-orchestrator",
                "lsf.eventing.listener.enabled=true",
                "lsf.eventing.idempotency.enabled=false",
                "lsf.eventing.consume-topics[0]=inventory.commands",
                "lsf.eventing.consume-topics[1]=payment.commands",
                "lsf.eventing.consume-topics[2]=saga.replies",
                "lsf.saga.store=jdbc",
                "lsf.saga.transport.mode=direct",
                "lsf.saga.timeout-scanner.enabled=false",
                "lsf.saga.jdbc.initialize-schema=always"
        }
)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                LsfSagaRuntimeIntegrationTest.INVENTORY_COMMANDS_TOPIC,
                LsfSagaRuntimeIntegrationTest.PAYMENT_COMMANDS_TOPIC,
                LsfSagaRuntimeIntegrationTest.REPLIES_TOPIC
        }
)
public class LsfSagaRuntimeIntegrationTest {

    static final String INVENTORY_COMMANDS_TOPIC = "inventory.commands";
    static final String PAYMENT_COMMANDS_TOPIC = "payment.commands";
    static final String REPLIES_TOPIC = "saga.replies";
    private static final String DEFINITION_NAME = "checkout-orchestrator";

    @Autowired
    private LsfSagaOrchestrator orchestrator;

    @Autowired
    private SagaInstanceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SagaRuntimeProbe probe;

    @Test
    void shouldCompleteSequentialSagaUsingDirectTransportAndJdbcStore() {
        assertThat(repository).isInstanceOf(JdbcSagaInstanceRepository.class);

        String orderId = "order-success-" + UUID.randomUUID();
        String correlationId = "corr-" + orderId;
        String requestId = "req-" + orderId;

        SagaInstance started = orchestrator.start(
                DEFINITION_NAME,
                orderId,
                CheckoutSagaState.initial(orderId, false),
                SagaStartOptions.builder()
                        .correlationId(correlationId)
                        .requestId(requestId)
                        .build()
        );

        assertThat(started.getStatus()).isEqualTo(SagaStatus.WAITING);
        assertThat(started.getSteps().getFirst().getStatus()).isEqualTo(SagaStepStatus.DISPATCHED);

        SagaInstance completed = awaitSagaStatus(orderId, SagaStatus.COMPLETED, Duration.ofSeconds(15));
        CheckoutSagaState finalState = objectMapper.convertValue(completed.getStateData(), CheckoutSagaState.class);

        RecordedEvent reserveCommand = probe.findCommand(orderId, "inventory.reserve.requested.v1");
        RecordedEvent inventoryReservedReply = probe.findReply(orderId, "inventory.reserved.v1");
        RecordedEvent paymentCommand = probe.findCommand(orderId, "payment.charge.requested.v1");
        RecordedEvent paymentChargedReply = probe.findReply(orderId, "payment.charged.v1");

        assertThat(reserveCommand.correlationId()).isEqualTo(correlationId);
        assertThat(reserveCommand.requestId()).isEqualTo(requestId);
        assertThat(paymentCommand.correlationId()).isEqualTo(correlationId);
        assertThat(paymentCommand.requestId()).isEqualTo(requestId);
        assertThat(paymentCommand.causationId()).isEqualTo(inventoryReservedReply.eventId());
        assertThat(paymentChargedReply.causationId()).isEqualTo(paymentCommand.eventId());

        assertThat(completed.getCorrelationId()).isEqualTo(correlationId);
        assertThat(completed.getRequestId()).isEqualTo(requestId);
        assertThat(completed.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(completed.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
        assertThat(completed.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
        assertThat(finalState.inventoryReserved()).isTrue();
        assertThat(finalState.paymentCharged()).isTrue();
        assertThat(finalState.inventoryReleased()).isFalse();
        assertThat(finalState.lastMessage()).isEqualTo("payment charged");

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM lsf_saga_instance WHERE saga_id = ?",
                String.class,
                orderId
        );
        assertThat(persistedStatus).isEqualTo(SagaStatus.COMPLETED.name());
    }

    @Test
    void shouldCompensateWhenPaymentReplyFails() {
        String orderId = "order-failure-" + UUID.randomUUID();
        String correlationId = "corr-" + orderId;
        String requestId = "req-" + orderId;

        orchestrator.start(
                DEFINITION_NAME,
                orderId,
                CheckoutSagaState.initial(orderId, true),
                SagaStartOptions.builder()
                        .correlationId(correlationId)
                        .requestId(requestId)
                        .build()
        );

        SagaInstance compensated = awaitSagaStatus(orderId, SagaStatus.COMPENSATED, Duration.ofSeconds(15));
        CheckoutSagaState finalState = objectMapper.convertValue(compensated.getStateData(), CheckoutSagaState.class);

        RecordedEvent inventoryReservedReply = probe.findReply(orderId, "inventory.reserved.v1");
        RecordedEvent paymentFailedReply = probe.findReply(orderId, "payment.charge.failed.v1");
        RecordedEvent compensationCommand = probe.findCommand(orderId, "inventory.release.requested.v1");
        RecordedEvent inventoryReleasedReply = probe.findReply(orderId, "inventory.released.v1");

        assertThat(paymentFailedReply.correlationId()).isEqualTo(correlationId);
        assertThat(paymentFailedReply.requestId()).isEqualTo(requestId);
        assertThat(paymentFailedReply.causationId()).isNotBlank();
        assertThat(compensationCommand.causationId()).isEqualTo(paymentFailedReply.eventId());
        assertThat(compensationCommand.requestId()).isEqualTo(requestId);
        assertThat(inventoryReleasedReply.causationId()).isEqualTo(compensationCommand.eventId());
        assertThat(inventoryReservedReply.correlationId()).isEqualTo(correlationId);

        assertThat(compensated.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(compensated.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPENSATED);
        assertThat(compensated.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.FAILED);
        assertThat(compensated.getFailureReason()).contains("card_declined");
        assertThat(finalState.inventoryReserved()).isTrue();
        assertThat(finalState.paymentCharged()).isFalse();
        assertThat(finalState.inventoryReleased()).isTrue();
        assertThat(finalState.lastMessage()).isEqualTo("inventory released");

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM lsf_saga_instance WHERE saga_id = ?",
                String.class,
                orderId
        );
        assertThat(persistedStatus).isEqualTo(SagaStatus.COMPENSATED.name());
    }

    private SagaInstance awaitSagaStatus(String sagaId, SagaStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        SagaInstance lastSnapshot = null;
        while (System.nanoTime() < deadline) {
            lastSnapshot = orchestrator.findById(sagaId).orElse(null);
            if (lastSnapshot != null && lastSnapshot.getStatus() == expected) {
                return lastSnapshot;
            }
            if (lastSnapshot != null && lastSnapshot.getStatus().isTerminal() && lastSnapshot.getStatus() != expected) {
                break;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for saga status " + expected);
            }
        }
        fail("Expected saga %s to reach status %s but was %s".formatted(
                sagaId,
                expected,
                lastSnapshot == null ? "<missing>" : lastSnapshot.getStatus()
        ));
        return null;
    }

    @EnableKafka
    @SpringBootApplication
    @Import({
            LsfSagaRuntimeIntegrationTest.ReserveInventoryHandler.class,
            LsfSagaRuntimeIntegrationTest.ReleaseInventoryHandler.class,
            LsfSagaRuntimeIntegrationTest.ChargePaymentHandler.class
    })
    static class LsfSagaRuntimeIntegrationTestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean(destroyMethod = "shutdown")
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SagaRuntimeProbe sagaRuntimeProbe() {
            return new SagaRuntimeProbe();
        }

        @Bean
        SagaDefinition<CheckoutSagaState> checkoutSagaDefinition() {
            return SagaDefinition.<CheckoutSagaState>builder(DEFINITION_NAME, CheckoutSagaState.class)
                    .step(SagaStep.<CheckoutSagaState>builder("reserveInventory")
                            .command(context -> context.command(
                                    INVENTORY_COMMANDS_TOPIC,
                                    context.state().orderId(),
                                    "inventory.reserve.requested.v1",
                                    context.state().orderId(),
                                    new ReserveInventoryCommand(context.state().orderId())
                            ))
                            .onReply("inventory.reserved.v1", InventoryReservedReply.class,
                                    (context, envelope, payload) -> SagaReplyDecision.success(
                                            context.state().withInventoryReserved(),
                                            "inventory reserved"
                                    ))
                            .compensation(compensation -> compensation
                                    .command(context -> context.command(
                                            INVENTORY_COMMANDS_TOPIC,
                                            context.state().orderId(),
                                            "inventory.release.requested.v1",
                                            context.state().orderId(),
                                            new ReleaseInventoryCommand(context.state().orderId())
                                    ))
                                    .onReply("inventory.released.v1", InventoryReleasedReply.class,
                                            (context, envelope, payload) -> SagaReplyDecision.success(
                                                    context.state().withInventoryReleased(),
                                                    "inventory released"
                                            ))
                            )
                            .timeout(Duration.ofSeconds(10))
                            .build())
                    .step(SagaStep.<CheckoutSagaState>builder("chargePayment")
                            .command(context -> context.command(
                                    PAYMENT_COMMANDS_TOPIC,
                                    context.state().orderId(),
                                    "payment.charge.requested.v1",
                                    context.state().orderId(),
                                    new ChargePaymentCommand(context.state().orderId(), context.state().failPayment())
                            ))
                            .onReply("payment.charged.v1", PaymentChargedReply.class,
                                    (context, envelope, payload) -> SagaReplyDecision.success(
                                            context.state().withPaymentCharged(),
                                            "payment charged"
                                    ))
                            .onReply("payment.charge.failed.v1", PaymentFailedReply.class,
                                    (context, envelope, payload) -> SagaReplyDecision.failure(
                                            context.state().withFailure(payload.reason()),
                                            payload.reason()
                                    ))
                            .timeout(Duration.ofSeconds(10))
                            .failureMode(SagaFailureMode.COMPENSATE)
                            .build())
                    .build();
        }
    }

    @Component
    public static class ReserveInventoryHandler {

        private final LsfPublisher publisher;
        private final ObjectMapper objectMapper;
        private final SagaRuntimeProbe probe;

        public ReserveInventoryHandler(LsfPublisher publisher, ObjectMapper objectMapper, SagaRuntimeProbe probe) {
            this.publisher = publisher;
            this.objectMapper = objectMapper;
            this.probe = probe;
        }

        @LsfEventHandler(value = "inventory.reserve.requested.v1", payload = ReserveInventoryCommand.class)
        public void onReserve(EventEnvelope envelope, ReserveInventoryCommand payload) {
            probe.recordCommand(payload.orderId(), envelope);
            EventEnvelope reply = EnvelopeBuilder.wrap(
                    objectMapper,
                    "inventory.reserved.v1",
                    1,
                    payload.orderId(),
                    envelope.getCorrelationId(),
                    envelope.getEventId(),
                    envelope.getRequestId(),
                    "inventory-service",
                    new InventoryReservedReply(payload.orderId())
            );
            probe.recordReply(payload.orderId(), reply);
            publisher.publish(REPLIES_TOPIC, payload.orderId(), reply).join();
        }
    }

    @Component
    public static class ReleaseInventoryHandler {

        private final LsfPublisher publisher;
        private final ObjectMapper objectMapper;
        private final SagaRuntimeProbe probe;

        public ReleaseInventoryHandler(LsfPublisher publisher, ObjectMapper objectMapper, SagaRuntimeProbe probe) {
            this.publisher = publisher;
            this.objectMapper = objectMapper;
            this.probe = probe;
        }

        @LsfEventHandler(value = "inventory.release.requested.v1", payload = ReleaseInventoryCommand.class)
        public void onRelease(EventEnvelope envelope, ReleaseInventoryCommand payload) {
            probe.recordCommand(payload.orderId(), envelope);
            EventEnvelope reply = EnvelopeBuilder.wrap(
                    objectMapper,
                    "inventory.released.v1",
                    1,
                    payload.orderId(),
                    envelope.getCorrelationId(),
                    envelope.getEventId(),
                    envelope.getRequestId(),
                    "inventory-service",
                    new InventoryReleasedReply(payload.orderId())
            );
            probe.recordReply(payload.orderId(), reply);
            publisher.publish(REPLIES_TOPIC, payload.orderId(), reply).join();
        }
    }

    @Component
    public static class ChargePaymentHandler {

        private final LsfPublisher publisher;
        private final ObjectMapper objectMapper;
        private final SagaRuntimeProbe probe;

        public ChargePaymentHandler(LsfPublisher publisher, ObjectMapper objectMapper, SagaRuntimeProbe probe) {
            this.publisher = publisher;
            this.objectMapper = objectMapper;
            this.probe = probe;
        }

        @LsfEventHandler(value = "payment.charge.requested.v1", payload = ChargePaymentCommand.class)
        public void onCharge(EventEnvelope envelope, ChargePaymentCommand payload) {
            probe.recordCommand(payload.orderId(), envelope);

            EventEnvelope reply = payload.failPayment()
                    ? EnvelopeBuilder.wrap(
                    objectMapper,
                    "payment.charge.failed.v1",
                    1,
                    payload.orderId(),
                    envelope.getCorrelationId(),
                    envelope.getEventId(),
                    envelope.getRequestId(),
                    "payment-service",
                    new PaymentFailedReply(payload.orderId(), "card_declined")
            )
                    : EnvelopeBuilder.wrap(
                    objectMapper,
                    "payment.charged.v1",
                    1,
                    payload.orderId(),
                    envelope.getCorrelationId(),
                    envelope.getEventId(),
                    envelope.getRequestId(),
                    "payment-service",
                    new PaymentChargedReply(payload.orderId())
            );

            probe.recordReply(payload.orderId(), reply);
            publisher.publish(REPLIES_TOPIC, payload.orderId(), reply).join();
        }
    }

    public record CheckoutSagaState(
            String orderId,
            boolean failPayment,
            boolean inventoryReserved,
            boolean paymentCharged,
            boolean inventoryReleased,
            String lastMessage
    ) {
        static CheckoutSagaState initial(String orderId, boolean failPayment) {
            return new CheckoutSagaState(orderId, failPayment, false, false, false, "created");
        }

        CheckoutSagaState withInventoryReserved() {
            return new CheckoutSagaState(orderId, failPayment, true, paymentCharged, inventoryReleased, "inventory reserved");
        }

        CheckoutSagaState withPaymentCharged() {
            return new CheckoutSagaState(orderId, failPayment, inventoryReserved, true, inventoryReleased, "payment charged");
        }

        CheckoutSagaState withFailure(String message) {
            return new CheckoutSagaState(orderId, failPayment, inventoryReserved, paymentCharged, inventoryReleased, message);
        }

        CheckoutSagaState withInventoryReleased() {
            return new CheckoutSagaState(orderId, failPayment, inventoryReserved, paymentCharged, true, "inventory released");
        }
    }

    public record ReserveInventoryCommand(String orderId) {
    }

    public record ReleaseInventoryCommand(String orderId) {
    }

    public record ChargePaymentCommand(String orderId, boolean failPayment) {
    }

    public record InventoryReservedReply(String orderId) {
    }

    public record InventoryReleasedReply(String orderId) {
    }

    public record PaymentChargedReply(String orderId) {
    }

    public record PaymentFailedReply(String orderId, String reason) {
    }

    static final class SagaRuntimeProbe {
        private final List<RecordedEvent> commands = new CopyOnWriteArrayList<>();
        private final List<RecordedEvent> replies = new CopyOnWriteArrayList<>();

        void recordCommand(String orderId, EventEnvelope envelope) {
            commands.add(RecordedEvent.from(orderId, envelope));
        }

        void recordReply(String orderId, EventEnvelope envelope) {
            replies.add(RecordedEvent.from(orderId, envelope));
        }

        RecordedEvent findCommand(String orderId, String eventType) {
            return commands.stream()
                    .filter(event -> event.orderId().equals(orderId))
                    .filter(event -> event.eventType().equals(eventType))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing command %s for order %s".formatted(eventType, orderId)
                    ));
        }

        RecordedEvent findReply(String orderId, String eventType) {
            return replies.stream()
                    .filter(event -> event.orderId().equals(orderId))
                    .filter(event -> event.eventType().equals(eventType))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing reply %s for order %s".formatted(eventType, orderId)
                    ));
        }
    }

    record RecordedEvent(
            String orderId,
            String eventId,
            String eventType,
            String correlationId,
            String causationId,
            String requestId
    ) {
        static RecordedEvent from(String orderId, EventEnvelope envelope) {
            return new RecordedEvent(
                    orderId,
                    envelope.getEventId(),
                    envelope.getEventType(),
                    envelope.getCorrelationId(),
                    envelope.getCausationId(),
                    envelope.getRequestId()
            );
        }
    }
}
