package com.myorg.lsf.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.LsfPublishOptions;
import com.myorg.lsf.eventing.LsfPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LsfKafkaEventingObservabilityITApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.application.name=lsf-observability-it",
                "lsf.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "lsf.kafka.schema-registry-url=mock://lsf-observability-it",
                "lsf.kafka.consumer.group-id=lsf-observability-it-group",
                "lsf.kafka.consumer.batch=false",
                "lsf.kafka.consumer.concurrency=1",
                "lsf.kafka.consumer.auto-offset-reset=earliest",
                "lsf.kafka.consumer.json-value-type=com.myorg.lsf.contracts.core.envelope.EventEnvelope",
                "lsf.eventing.producer-name=payments-service",
                "lsf.eventing.consume-topics=payments.runtime",
                "lsf.eventing.listener.enabled=true",
                "lsf.observability.enabled=true",
                "lsf.observability.mdc-enabled=true",
                "lsf.observability.metrics-enabled=true",
                "lsf.observability.tracing-enabled=true",
                "lsf.observability.tag-topic=true",
                "lsf.observability.tag-event-type=true",
                "lsf.observability.tag-outcome=true"
        }
)
@EmbeddedKafka(partitions = 1, topics = LsfKafkaEventingObservabilityIT.TOPIC)
class LsfKafkaEventingObservabilityIT {

    static final String TOPIC = "payments.runtime";
    private static final String EVENT_TYPE = "payments.authorized.v1";

    @org.springframework.beans.factory.annotation.Autowired
    private LsfPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    private LsfDispatcher dispatcher;

    @org.springframework.beans.factory.annotation.Autowired
    private EventCapture eventCapture;

    @org.springframework.beans.factory.annotation.Autowired
    private ObservationCapture observationCapture;

    @org.springframework.beans.factory.annotation.Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldPublishDispatchAndObserveAcrossKafkaEventingAndObservability() throws Exception {
        publisher.publish(
                TOPIC,
                "ORD-2001",
                EVENT_TYPE,
                "order-2001",
                new PaymentAuthorized("ORD-2001"),
                LsfPublishOptions.builder()
                        .correlationId("corr-2001")
                        .causationId("cause-2001")
                        .requestId("req-2001")
                        .producer("payments-service")
                        .build()
        ).get(10, TimeUnit.SECONDS);

        assertThat(eventCapture.await(Duration.ofSeconds(10))).isTrue();
        HandledEventSnapshot snapshot = eventCapture.snapshot();

        assertThat(dispatcher).isInstanceOf(ObservingLsfDispatcher.class);
        assertThat(snapshot.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(snapshot.payloadOrderId()).isEqualTo("ORD-2001");
        assertThat(snapshot.envelopeCorrelationId()).isEqualTo("corr-2001");
        assertThat(snapshot.envelopeCausationId()).isEqualTo("cause-2001");
        assertThat(snapshot.envelopeRequestId()).isEqualTo("req-2001");
        assertThat(snapshot.contextCorrelationId()).isEqualTo("corr-2001");
        assertThat(snapshot.contextCausationId()).isEqualTo("cause-2001");
        assertThat(snapshot.contextRequestId()).isEqualTo("req-2001");
        assertThat(snapshot.mdcCorrelationId()).isEqualTo("corr-2001");
        assertThat(snapshot.mdcCausationId()).isEqualTo("cause-2001");
        assertThat(snapshot.mdcRequestId()).isEqualTo("req-2001");

        double handledSuccess = meterRegistry.get("lsf.event.handled.success")
                .tag("service", "lsf-observability-it")
                .counter()
                .count();
        assertThat(handledSuccess).isEqualTo(1.0d);

        Timer processingTimer = meterRegistry.get("lsf.event.processing")
                .tag("service", "lsf-observability-it")
                .tag("eventType", EVENT_TYPE)
                .tag("outcome", "success")
                .timer();
        assertThat(processingTimer.count()).isEqualTo(1L);

        assertThat(observationCapture.completedObservations())
                .anySatisfy(observation -> {
                    assertThat(observation.name()).isEqualTo("lsf.event.dispatch");
                    assertThat(observation.lowCardinalityTags())
                            .containsEntry("event.type", EVENT_TYPE)
                            .containsEntry("outcome", "success");
                });
    }
}

@EnableKafka
@SpringBootApplication
class LsfKafkaEventingObservabilityITApplication {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    ObservationCapture observationCapture() {
        return new ObservationCapture();
    }

    @Bean
    ObservationRegistry observationRegistry(ObservationCapture observationCapture) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(observationCapture);
        return registry;
    }

    @Bean
    EventCapture eventCapture() {
        return new EventCapture();
    }
}

record HandledEventSnapshot(
        String eventType,
        String payloadOrderId,
        String envelopeCorrelationId,
        String envelopeCausationId,
        String envelopeRequestId,
        String contextCorrelationId,
        String contextCausationId,
        String contextRequestId,
        String mdcCorrelationId,
        String mdcCausationId,
        String mdcRequestId
) {
}

class EventCapture {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<HandledEventSnapshot> snapshot = new AtomicReference<>();

    void record(HandledEventSnapshot handledEventSnapshot) {
        snapshot.set(handledEventSnapshot);
        latch.countDown();
    }

    boolean await(Duration timeout) throws InterruptedException {
        return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    HandledEventSnapshot snapshot() {
        return snapshot.get();
    }
}

class ObservationCapture implements ObservationHandler<Observation.Context> {

    private final List<CompletedObservation> completedObservations = new CopyOnWriteArrayList<>();

    @Override
    public void onStop(Observation.Context context) {
        Map<String, String> tags = new LinkedHashMap<>();
        context.getLowCardinalityKeyValues().forEach(keyValue -> tags.put(keyValue.getKey(), keyValue.getValue()));
        completedObservations.add(new CompletedObservation(context.getName(), tags));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    List<CompletedObservation> completedObservations() {
        return completedObservations;
    }
}

record CompletedObservation(String name, Map<String, String> lowCardinalityTags) {
}
