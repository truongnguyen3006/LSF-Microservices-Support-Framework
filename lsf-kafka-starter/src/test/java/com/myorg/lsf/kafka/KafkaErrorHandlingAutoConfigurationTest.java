package com.myorg.lsf.kafka;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaErrorHandlingAutoConfigurationTest {

    private final ApplicationContextRunner errorHandlingRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaErrorHandlingAutoConfiguration.class))
            .withPropertyValues(
                    "lsf.kafka.bootstrap-servers=localhost:9092",
                    "lsf.kafka.schema-registry-url=mock://lsf",
                    "lsf.kafka.dlq.enabled=true"
            );

    @Test
    void shouldFailFastWhenDlqEnabledWithoutKafkaTemplate() {
        errorHandlingRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("lsf.kafka.dlq.enabled=true")
                    .hasMessageContaining("KafkaTemplate<String,Object>");
        });
    }

    @Test
    void shouldTagRetryDlqAndRecoveryFailureMetricsWithReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaErrorHandlingAutoConfiguration.LsfKafkaRetryDlqMetricsListener listener =
                new KafkaErrorHandlingAutoConfiguration.LsfKafkaRetryDlqMetricsListener(
                        "billing-service",
                        singletonProvider(registry),
                        new DefaultLsfDlqReasonClassifier()
                );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("payments", 2, 17L, "ORD-17", "payload");

        listener.failedDelivery(record, new RuntimeException("transient"), 2);
        listener.recovered(record, new LsfNonRetryableException("PAYLOAD_INVALID", "bad payload"));
        listener.recoveryFailed(record, new RuntimeException("original"), new SerializationException("serde failure"));

        assertThat(registry.get("lsf.kafka.retry")
                .tags(
                        "service", "billing-service",
                        "topic", "payments",
                        "exception", "RuntimeException",
                        "reason", LsfDlqReason.RETRY_EXHAUSTED.code(),
                        "non_retryable", "false"
                )
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("lsf.kafka.dlq")
                .tags(
                        "service", "billing-service",
                        "topic", "payments",
                        "exception", "LsfNonRetryableException",
                        "reason", "PAYLOAD_INVALID",
                        "non_retryable", "true"
                )
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("lsf.kafka.recovery_failed")
                .tags(
                        "service", "billing-service",
                        "topic", "payments",
                        "exception", "SerializationException",
                        "reason", LsfDlqReason.DESERIALIZATION.code(),
                        "non_retryable", "true"
                )
                .counter()
                .count()).isEqualTo(1.0);
    }

    private static <T> ObjectProvider<T> singletonProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
