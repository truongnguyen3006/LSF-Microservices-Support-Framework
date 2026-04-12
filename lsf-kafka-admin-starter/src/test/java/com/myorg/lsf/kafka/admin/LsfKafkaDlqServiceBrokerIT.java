package com.myorg.lsf.kafka.admin;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.kafka.LsfDlqHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = LsfKafkaDlqServiceBrokerIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class LsfKafkaDlqServiceBrokerIT {

    private static final String SOURCE_TOPIC = "orders.runtime";
    private static final String DLQ_TOPIC = SOURCE_TOPIC + ".DLQ";
    private static final String SCHEMA_REGISTRY_SCOPE = "mock://lsf-kafka-admin-broker-it";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.1")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
        String bootstrapServers = bootstrapServers();
        ensureTopics(bootstrapServers);

        registry.add("spring.application.name", () -> "lsf-kafka-admin-it");
        registry.add("lsf.kafka.bootstrap-servers", () -> bootstrapServers);
        registry.add("lsf.kafka.schema-registry-url", () -> SCHEMA_REGISTRY_SCOPE);
        registry.add("lsf.kafka.consumer.group-id", () -> "lsf-kafka-admin-it-group");
        registry.add("lsf.kafka.consumer.batch", () -> "false");
        registry.add("lsf.kafka.consumer.concurrency", () -> "1");
        registry.add("lsf.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("lsf.kafka.consumer.json-value-type", EventEnvelope.class::getName);
        registry.add("lsf.kafka.admin.enabled", () -> "true");
        registry.add("lsf.kafka.admin.allow-replay", () -> "true");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @org.springframework.beans.factory.annotation.Autowired
    private LsfKafkaDlqService service;

    @org.springframework.beans.factory.annotation.Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldInspectAndReplayDlqRecordsAgainstRealBroker() throws Exception {
        EventEnvelope envelope = EventEnvelope.builder()
                .eventId("evt-" + UUID.randomUUID())
                .eventType("orders.failed.v1")
                .aggregateId("order-1001")
                .correlationId("corr-1001")
                .causationId("cause-1001")
                .requestId("req-1001")
                .producer("orders-service")
                .occurredAtMs(System.currentTimeMillis())
                .payload(new com.fasterxml.jackson.databind.ObjectMapper()
                        .createObjectNode()
                        .put("orderId", "ORD-1001"))
                .build();

        ProducerRecord<String, Object> producedDlqRecord = new ProducerRecord<>(DLQ_TOPIC, "ORD-1001", envelope);
        addHeader(producedDlqRecord.headers(), CoreHeaders.EVENT_ID, envelope.getEventId());
        addHeader(producedDlqRecord.headers(), CoreHeaders.EVENT_TYPE, envelope.getEventType());
        addHeader(producedDlqRecord.headers(), CoreHeaders.CORRELATION_ID, envelope.getCorrelationId());
        addHeader(producedDlqRecord.headers(), CoreHeaders.CAUSATION_ID, envelope.getCausationId());
        addHeader(producedDlqRecord.headers(), CoreHeaders.REQUEST_ID, envelope.getRequestId());
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.REASON, "NON_RETRYABLE");
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.NON_RETRYABLE, "true");
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.ORIGINAL_TOPIC, SOURCE_TOPIC);
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.ORIGINAL_PARTITION, "0");
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.ORIGINAL_OFFSET, "42");
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.EXCEPTION_CLASS, IllegalStateException.class.getName());
        addHeader(producedDlqRecord.headers(), LsfDlqHeaders.EXCEPTION_MESSAGE, "forced-dlq");

        kafkaTemplate.send(producedDlqRecord).get(10, TimeUnit.SECONDS);

        LsfKafkaDlqRecordView listedDlqRecord = awaitDlqRecord();
        assertThat(listedDlqRecord.topic()).isEqualTo(DLQ_TOPIC);
        assertThat(listedDlqRecord.originalTopic()).isEqualTo(SOURCE_TOPIC);
        assertThat(listedDlqRecord.eventId()).isEqualTo(envelope.getEventId());
        assertThat(listedDlqRecord.correlationId()).isEqualTo("corr-1001");
        assertThat(listedDlqRecord.causationId()).isEqualTo("cause-1001");
        assertThat(listedDlqRecord.requestId()).isEqualTo("req-1001");
        assertThat(listedDlqRecord.reason()).isEqualTo("NON_RETRYABLE");
        assertThat(listedDlqRecord.nonRetryable()).isTrue();
        assertThat(listedDlqRecord.headers()).containsEntry(CoreHeaders.CORRELATION_ID, "corr-1001");
        assertThat(listedDlqRecord.headers()).containsEntry(CoreHeaders.CAUSATION_ID, "cause-1001");
        assertThat(listedDlqRecord.headers()).containsEntry(CoreHeaders.REQUEST_ID, "req-1001");

        LsfKafkaReplayResult replayResult = service.replay(new LsfKafkaReplayRequest(
                DLQ_TOPIC,
                listedDlqRecord.partition(),
                listedDlqRecord.offset(),
                null,
                false
        ));

        assertThat(replayResult.targetTopic()).isEqualTo(SOURCE_TOPIC);
        assertThat(replayResult.eventId()).isEqualTo(envelope.getEventId());

        ConsumerRecord<String, Object> replayedRecord = awaitReplayedRecord();
        assertThat(headerValue(replayedRecord.headers(), LsfKafkaReplayHeaders.SOURCE_TOPIC)).isEqualTo(DLQ_TOPIC);
        assertThat(headerValue(replayedRecord.headers(), LsfKafkaReplayHeaders.SOURCE_PARTITION))
                .isEqualTo(String.valueOf(listedDlqRecord.partition()));
        assertThat(headerValue(replayedRecord.headers(), LsfKafkaReplayHeaders.SOURCE_OFFSET))
                .isEqualTo(String.valueOf(listedDlqRecord.offset()));
        assertThat(headerValue(replayedRecord.headers(), CoreHeaders.EVENT_ID)).isEqualTo(envelope.getEventId());
        assertThat(headerValue(replayedRecord.headers(), CoreHeaders.CORRELATION_ID)).isEqualTo("corr-1001");
        assertThat(headerValue(replayedRecord.headers(), CoreHeaders.CAUSATION_ID)).isEqualTo("cause-1001");
        assertThat(headerValue(replayedRecord.headers(), CoreHeaders.REQUEST_ID)).isEqualTo("req-1001");
        assertThat(headerValue(replayedRecord.headers(), LsfDlqHeaders.REASON)).isNull();
        assertThat(headerValue(replayedRecord.headers(), LsfDlqHeaders.EXCEPTION_CLASS)).isNull();

        double replaySuccess = meterRegistry.get("lsf.kafka.replay.success")
                .tag("service", "lsf-kafka-admin-it")
                .tag("source_topic", DLQ_TOPIC)
                .tag("target_topic", SOURCE_TOPIC)
                .counter()
                .count();
        assertThat(replaySuccess).isEqualTo(1.0d);
    }

    private LsfKafkaDlqRecordView awaitDlqRecord() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            List<LsfKafkaDlqRecordView> records = service.listRecords(DLQ_TOPIC, 0, 10, null);
            if (!records.isEmpty()) {
                return records.getFirst();
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Timed out waiting for DLQ record on " + DLQ_TOPIC);
    }

    private ConsumerRecord<String, Object> awaitReplayedRecord() throws Exception {
        try (Consumer<String, Object> consumer = consumerFactory.createConsumer(
                "lsf-kafka-admin-probe",
                "lsf-kafka-admin-probe-" + UUID.randomUUID()
        )) {
            consumer.subscribe(List.of(SOURCE_TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, Object> record : records.records(SOURCE_TOPIC)) {
                    if (record.headers().lastHeader(LsfKafkaReplayHeaders.SOURCE_TOPIC) != null) {
                        return record;
                    }
                }
            }
        }
        throw new IllegalStateException("Timed out waiting for replayed record on " + SOURCE_TOPIC);
    }

    private static String bootstrapServers() {
        return KAFKA.getBootstrapServers()
                .replace("PLAINTEXT://", "")
                .replace("SASL_PLAINTEXT://", "")
                .replace("SASL_SSL://", "")
                .replace("SSL://", "");
    }

    private static void ensureTopics(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(props)) {
            try {
                adminClient.createTopics(List.of(
                        new NewTopic(SOURCE_TOPIC, 1, (short) 1),
                        new NewTopic(DLQ_TOPIC, 1, (short) 1)
                )).all().get(15, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                if (!(ex.getCause() instanceof TopicExistsException)) {
                    throw ex;
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to provision Kafka topics for broker IT", ex);
        }
    }

    private static void addHeader(Headers headers, String key, String value) {
        headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String headerValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
