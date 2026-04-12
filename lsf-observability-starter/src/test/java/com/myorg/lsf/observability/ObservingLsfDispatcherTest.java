package com.myorg.lsf.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservingLsfDispatcherTest {

    private final LsfObservabilityProperties props = new LsfObservabilityProperties();
    private final EventEnvelope envelope = EventEnvelope.builder()
            .eventId("evt-1")
            .eventType("payment.requested.v1")
            .correlationId("corr-1")
            .causationId("cause-1")
            .requestId("req-1")
            .traceHeaders(Map.of(
                    CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
            ))
            .payload(new ObjectMapper().createObjectNode().put("orderId", "ORD-1"))
            .build();

    @AfterEach
    void tearDown() {
        MDC.clear();
        LsfDispatchOutcome.clear();
    }

    @Test
    void shouldRecordSuccessMetricsPopulateMdcAndOpenObservationScope() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LsfMetrics metrics = metrics(registry);
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        AtomicReference<String> corrId = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> spanId = new AtomicReference<>();
        AtomicBoolean observationOpen = new AtomicBoolean(false);

        ObservingLsfDispatcher dispatcher = new ObservingLsfDispatcher(env -> {
            corrId.set(MDC.get("corrId"));
            traceId.set(MDC.get("traceId"));
            spanId.set(MDC.get("spanId"));
            observationOpen.set(observationRegistry.getCurrentObservation() != null);
        }, props, metrics, observationRegistry);

        dispatcher.dispatch(envelope);

        assertThat(corrId.get()).isEqualTo("corr-1");
        assertThat(traceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(spanId.get()).isEqualTo("bbbbbbbbbbbbbbbb");
        assertThat(observationOpen.get()).isTrue();
        assertThat(counterCount(registry, "lsf.event.handled.success")).isEqualTo(1.0);
        assertThat(timerCount(registry, "success")).isEqualTo(1L);
        assertThat(MDC.get("corrId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void shouldRecordDuplicateOutcomeWithoutIncrementingHandledSuccess() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LsfMetrics metrics = metrics(registry);

        ObservingLsfDispatcher dispatcher = new ObservingLsfDispatcher(env -> LsfDispatchOutcome.markDuplicate(),
                props,
                metrics,
                ObservationRegistry.create());

        dispatcher.dispatch(envelope);

        assertThat(counterCount(registry, "lsf.event.duplicate")).isEqualTo(1.0);
        assertThat(counterCount(registry, "lsf.event.handled.success")).isEqualTo(0.0);
        assertThat(timerCount(registry, LsfDispatchOutcome.DUPLICATE)).isEqualTo(1L);
    }

    @Test
    void shouldRecordFailureMetricsAndClearMdcWhenDelegateThrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LsfMetrics metrics = metrics(registry);
        ObservingLsfDispatcher dispatcher = new ObservingLsfDispatcher(env -> {
            assertThat(MDC.get("eventId")).isEqualTo("evt-1");
            throw new IllegalStateException("boom");
        }, props, metrics, ObservationRegistry.create());

        assertThatThrownBy(() -> dispatcher.dispatch(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(counterCount(registry, "lsf.event.handled.fail")).isEqualTo(1.0);
        assertThat(timerCount(registry, "fail")).isEqualTo(1L);
        assertThat(MDC.get("eventId")).isNull();
        assertThat(MDC.get("corrId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    private LsfMetrics metrics(SimpleMeterRegistry registry) {
        LsfMetrics metrics = new LsfMetrics(registry, "billing-service", props);
        metrics.preRegisterBaseMeters();
        return metrics;
    }

    private double counterCount(SimpleMeterRegistry registry, String name) {
        return registry.get(name)
                .tag("service", "billing-service")
                .counter()
                .count();
    }

    private long timerCount(SimpleMeterRegistry registry, String outcome) {
        return registry.get("lsf.event.processing")
                .tags(
                        "service", "billing-service",
                        "outcome", outcome,
                        "eventType", "payment.requested.v1"
                )
                .timer()
                .count();
    }
}
