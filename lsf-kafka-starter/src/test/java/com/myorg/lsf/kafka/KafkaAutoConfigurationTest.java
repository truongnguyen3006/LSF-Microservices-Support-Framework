package com.myorg.lsf.kafka;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    KafkaProducerAutoConfiguration.class,
                    KafkaErrorHandlingAutoConfiguration.class,
                    KafkaConsumerAutoConfiguration.class
            ))
            .withPropertyValues(
                    "lsf.kafka.bootstrap-servers=localhost:9092",
                    "lsf.kafka.schema-registry-url=mock://lsf",
                    "lsf.kafka.consumer.group-id=test-group",
                    "lsf.kafka.consumer.batch=false",
                    "lsf.kafka.consumer.concurrency=3",
                    "lsf.kafka.consumer.max-poll-records=123",
                    "lsf.kafka.consumer.retry.attempts=5",
                    "lsf.kafka.consumer.retry.backoff=250ms",
                    "lsf.kafka.dlq.enabled=false"
            );

    @Test
    void shouldCreateProducerConsumerAndAdminBeansFromLsfProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            KafkaAutoConfiguration.LsfKafkaMarker marker = context.getBean(KafkaAutoConfiguration.LsfKafkaMarker.class);
            assertThat(marker.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(marker.schemaRegistryUrl()).isEqualTo("mock://lsf");

            KafkaAdmin kafkaAdmin = context.getBean(KafkaAdmin.class);
            assertThat(kafkaAdmin.getConfigurationProperties())
                    .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

            DefaultKafkaProducerFactory<String, Object> producerFactory =
                    (DefaultKafkaProducerFactory<String, Object>) context.getBean(ProducerFactory.class);
            assertThat(producerFactory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                    .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                    .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                    .containsEntry(ProducerConfig.RETRIES_CONFIG, 10)
                    .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
                    .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                    .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 5)
                    .containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, 65536);

            DefaultKafkaConsumerFactory<String, Object> consumerFactory =
                    (DefaultKafkaConsumerFactory<String, Object>) context.getBean(ConsumerFactory.class);
            assertThat(consumerFactory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                    .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                    .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class)
                    .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "test-group")
                    .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                    .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                    .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 123);

            ConcurrentKafkaListenerContainerFactory<?, ?> containerFactory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(containerFactory.isBatchListener()).isFalse();
            assertThat(readField(containerFactory, "concurrency")).isEqualTo(3);
            assertThat(containerFactory.getContainerProperties().getAckMode())
                    .isEqualTo(ContainerProperties.AckMode.RECORD);
            assertThat(extractCommonErrorHandler(containerFactory))
                    .isSameAs(context.getBean(CommonErrorHandler.class))
                    .isInstanceOf(DefaultErrorHandler.class);
        });
    }

    @Test
    void shouldAttachLsfRetryMetricsListenerToDefaultErrorHandler() {
        runner.run(context -> {
            DefaultErrorHandler handler = context.getBean(DefaultErrorHandler.class);
            List<Object> listeners = retryListeners(handler);

            assertThat(listeners)
                    .anyMatch(KafkaErrorHandlingAutoConfiguration.LsfKafkaRetryDlqMetricsListener.class::isInstance);
        });
    }

    private static Object extractCommonErrorHandler(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        try {
            Method getter = factory.getClass().getMethod("getCommonErrorHandler");
            return getter.invoke(factory);
        } catch (NoSuchMethodException ignored) {
            return readField(factory, "commonErrorHandler");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect commonErrorHandler", ex);
        }
    }

    private static List<Object> retryListeners(DefaultErrorHandler handler) {
        try {
            Method getter = handler.getClass().getSuperclass().getDeclaredMethod("getRetryListeners");
            getter.setAccessible(true);
            Object result = getter.invoke(handler);
            if (result instanceof Object[] array) {
                return List.of(array);
            }
            if (result instanceof Collection<?> collection) {
                return List.copyOf(collection);
            }
        } catch (NoSuchMethodException ignored) {
            Object failureTracker = readField(handler, "failureTracker");
            Object result = failureTracker == null ? null : readField(failureTracker, "retryListeners");
            if (result instanceof Object[] array) {
                return List.of(array);
            }
            if (result instanceof Collection<?> collection) {
                return List.copyOf(collection);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect retry listeners", ex);
        }
        return List.of();
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to read field " + fieldName, ex);
            }
        }
        return null;
    }
}
