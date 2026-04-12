package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EmbeddedKafka(partitions = 1, topics = {OutboxCrashRestartTest.TOPIC})
@SpringBootTest(
        classes = {OutboxPublisherITApp.class, OutboxCrashRestartTest.HooksConfig.class},
        properties = {
                "lsf.outbox.enabled=true",
                "lsf.outbox.publisher.enabled=true",
                "lsf.outbox.publisher.scheduling-enabled=false",
                "lsf.outbox.publisher.batch-size=10",
                "lsf.outbox.publisher.lease=200ms",
                "lsf.outbox.publisher.backoff-base=50ms",
                "lsf.outbox.publisher.backoff-max=500ms",
                "lsf.outbox.publisher.max-retries=5",
                "lsf.outbox.publisher.send-timeout=5s",

                "spring.datasource.url=jdbc:h2:mem:outbox_it1;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:META-INF/spring/lsf/sql/mysql",

                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
                "spring.kafka.consumer.properties.spring.json.value.default.type=com.myorg.lsf.contracts.core.envelope.EventEnvelope",
                "spring.kafka.consumer.auto-offset-reset=earliest"
        }
)
class OutboxCrashRestartTest {

    static final String TOPIC = "demo-topic";

    @Autowired OutboxWriter writer;
    @Autowired OutboxPublisher publisher;
    @Autowired JdbcOutboxRepository repo;

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ConsumerFactory<String, EventEnvelope> consumerFactory;
    @Autowired ObjectMapper mapper;

    @Test
    void crashAfterClaim_thenRestart_shouldStillPublish() throws Exception {
        String eventId = "E_CRASH_" + java.util.UUID.randomUUID();

        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType("demo.order.created.v1")
                .version(1)
                .producer("it")
                .occurredAtMs(System.currentTimeMillis())
                .payload(mapper.createObjectNode().put("x", 1))
                .build();

        writer.append(env, TOPIC, "k1");

        // 1st run: crash after claim (hook throws)
        try {
            publisher.runOnce();
            fail("Expected simulated crash");
        } catch (RuntimeException ignored) {}

        assertEquals("PROCESSING", repo.statusByEventId(eventId));

        // wait lease expire
        Thread.sleep(250);

        // 2nd run: should reclaim + publish + mark SENT
        publisher.runOnce();

        // verify Kafka got the event
        Consumer<String, EventEnvelope> c = consumerFactory.createConsumer("it", "it");
        c.subscribe(java.util.List.of(TOPIC));
        ConsumerRecord<String, EventEnvelope> rec =
                KafkaTestUtils.getSingleRecord(c, TOPIC, Duration.ofSeconds(5));
        assertNotNull(rec.value());
        assertEquals(eventId, rec.value().getEventId());

        // verify DB status
        assertEquals("SENT", repo.statusByEventId(eventId));
        c.close();
    }

    @TestConfiguration
    static class HooksConfig {
        private final AtomicBoolean crashOnce = new AtomicBoolean(true);

        @Bean
        OutboxPublisherHooks outboxPublisherHooks() {
            return new OutboxPublisherHooks() {
                @Override
                public void afterClaim(java.util.List<OutboxRow> claimedRows) {
                    if (crashOnce.compareAndSet(true, false)) {
                        throw new RuntimeException("Simulated crash after claim");
                    }
                }
            };
        }

        @Bean
        ConsumerFactory<String, EventEnvelope> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("it-g1", "false", broker);
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer");
            props.put("spring.json.trusted.packages", "*");
            props.put("spring.json.value.default.type", "com.myorg.lsf.contracts.core.envelope.EventEnvelope");
            props.put("auto.offset.reset", "earliest");
            return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate(@Value("${spring.embedded.kafka.brokers}") String brokers) {
            Map<String, Object> props = new java.util.HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }

    }


}
