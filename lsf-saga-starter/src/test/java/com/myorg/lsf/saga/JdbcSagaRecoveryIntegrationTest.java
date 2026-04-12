package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSagaRecoveryIntegrationTest {

    @Test
    void shouldResumePersistedWaitingSagaWithNewOrchestratorInstance() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        try {
            DatabasePopulatorUtils.execute(
                    new ResourceDatabasePopulator(new ClassPathResource("META-INF/spring/lsf/sql/lsf_saga.sql")),
                    database
            );

            ObjectMapper mapper = new ObjectMapper();
            LsfSagaProperties properties = new LsfSagaProperties();
            JdbcSagaInstanceRepository repository = new JdbcSagaInstanceRepository(
                    new JdbcTemplate(database),
                    mapper,
                    properties
            );
            MutableClock clock = new MutableClock(Instant.parse("2026-04-08T00:00:00Z"));

            RecordingSagaEventPublisher firstPublisher = new RecordingSagaEventPublisher();
            DefaultLsfSagaOrchestrator firstOrchestrator = new DefaultLsfSagaOrchestrator(
                    new SagaDefinitionRegistry(List.of(testDefinition())),
                    repository,
                    firstPublisher,
                    mapper,
                    clock,
                    properties,
                    "checkout-orchestrator",
                    null
            );

            firstOrchestrator.start(
                    "checkout-recovery",
                    "saga-recovery",
                    new RecoverySagaState("ORD-RECOVERY", false, false),
                    SagaStartOptions.builder()
                            .correlationId("corr-recovery")
                            .requestId("req-recovery")
                            .build()
            );

            PublishedMessage reserveCommand = firstPublisher.messages.getFirst();
            assertThat(firstOrchestrator.onEvent(replyEnvelope(
                    "evt-recovery-inventory",
                    "inventory.reserved.v1",
                    "corr-recovery",
                    reserveCommand.envelope().getEventId(),
                    "req-recovery",
                    new RecoveryInventoryReservedReply("ORD-RECOVERY")
            ))).isTrue();

            SagaInstance waiting = repository.findById("saga-recovery").orElseThrow();
            assertThat(waiting.getStatus()).isEqualTo(SagaStatus.WAITING);
            assertThat(waiting.getCurrentStep()).isEqualTo("chargePayment");
            assertThat(waiting.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
            assertThat(waiting.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.DISPATCHED);

            PublishedMessage paymentCommand = firstPublisher.messages.get(1);

            RecordingSagaEventPublisher secondPublisher = new RecordingSagaEventPublisher();
            DefaultLsfSagaOrchestrator restartedOrchestrator = new DefaultLsfSagaOrchestrator(
                    new SagaDefinitionRegistry(List.of(testDefinition())),
                    repository,
                    secondPublisher,
                    mapper,
                    clock,
                    properties,
                    "checkout-orchestrator",
                    null
            );

            assertThat(restartedOrchestrator.onEvent(replyEnvelope(
                    "evt-recovery-payment",
                    "payment.charged.v1",
                    "corr-recovery",
                    paymentCommand.envelope().getEventId(),
                    "req-recovery",
                    new RecoveryPaymentChargedReply("ORD-RECOVERY")
            ))).isTrue();

            SagaInstance completed = repository.findById("saga-recovery").orElseThrow();
            RecoverySagaState state = mapper.convertValue(completed.getStateData(), RecoverySagaState.class);

            assertThat(completed.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(completed.getSteps().get(0).getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
            assertThat(completed.getSteps().get(1).getStatus()).isEqualTo(SagaStepStatus.COMPLETED);
            assertThat(state.inventoryReserved()).isTrue();
            assertThat(state.paymentCharged()).isTrue();
            assertThat(restartedOrchestrator.findById("saga-recovery")).isPresent();
            assertThat(secondPublisher.messages).isEmpty();
        } finally {
            database.shutdown();
        }
    }

    private static SagaDefinition<RecoverySagaState> testDefinition() {
        return SagaDefinition.<RecoverySagaState>builder("checkout-recovery", RecoverySagaState.class)
                .step(SagaStep.<RecoverySagaState>builder("reserveInventory")
                        .command(context -> context.command(
                                "inventory-commands",
                                context.state().orderId(),
                                "inventory.reserve.requested.v1",
                                new RecoveryReserveInventoryCommand(context.state().orderId())
                        ))
                        .onReply("inventory.reserved.v1", RecoveryInventoryReservedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.success(
                                        context.state().withInventoryReserved(),
                                        "inventory reserved"
                                ))
                        .timeout(Duration.ofSeconds(30))
                        .build())
                .step(SagaStep.<RecoverySagaState>builder("chargePayment")
                        .command(context -> context.command(
                                "payment-commands",
                                context.state().orderId(),
                                "payment.charge.requested.v1",
                                new RecoveryChargePaymentCommand(context.state().orderId())
                        ))
                        .onReply("payment.charged.v1", RecoveryPaymentChargedReply.class,
                                (context, envelope, payload) -> SagaReplyDecision.success(
                                        context.state().withPaymentCharged(),
                                        "payment charged"
                                ))
                        .timeout(Duration.ofSeconds(30))
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
        private final Instant instant;

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
    }

    private record RecoverySagaState(
            String orderId,
            boolean inventoryReserved,
            boolean paymentCharged
    ) {
        private RecoverySagaState withInventoryReserved() {
            return new RecoverySagaState(orderId, true, paymentCharged);
        }

        private RecoverySagaState withPaymentCharged() {
            return new RecoverySagaState(orderId, inventoryReserved, true);
        }
    }

    private record RecoveryReserveInventoryCommand(String orderId) {
    }

    private record RecoveryChargePaymentCommand(String orderId) {
    }

    private record RecoveryInventoryReservedReply(String orderId) {
    }

    private record RecoveryPaymentChargedReply(String orderId) {
    }
}
