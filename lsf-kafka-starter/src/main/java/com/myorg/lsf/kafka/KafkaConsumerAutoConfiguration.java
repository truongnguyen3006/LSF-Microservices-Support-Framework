package com.myorg.lsf.kafka;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
//lắng nghe tin nhắn từ Kafka.
@AutoConfiguration(before = org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class)
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
@EnableConfigurationProperties(KafkaProperties.class)
@Slf4j
public class KafkaConsumerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    //nạp các cấu hình kết nối
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties props, SerdeFactory serdeFactory) {
        Map<String, Object> c = new HashMap<>();
        c.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());

        c.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        c.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, props.getSchemaRegistryUrl());
        c.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, serdeFactory.valueDeserializerClass());

        c.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, props.getConsumer().getMaxPollRecords());
        // Let Spring Kafka manage commits (plays nicely with retries/DLQ)
        // Tắt auto-commit giúp ứng dụng chủ động kiểm soát việc xác nhận đã đọc tin nhắn
        c.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Reduce "missed" messages when running local demos (fresh group w/o offsets)
        c.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getConsumer().getAutoOffsetReset());
        if (StringUtils.hasText(props.getConsumer().getJsonValueType())) {
            c.put("json.value.type", props.getConsumer().getJsonValueType());
        }

        //set group.id nếu user cấu hình
        if (StringUtils.hasText(props.getConsumer().getGroupId())) {
            c.put(ConsumerConfig.GROUP_ID_CONFIG, props.getConsumer().getGroupId());
        }

        return new DefaultKafkaConsumerFactory<>(c);
    }

    @Bean
    @ConditionalOnMissingBean
    // Cho phép tạo ra nhiều luồng (threads) để đọc tin nhắn song song dựa trên thông số concurrency
    //“Concurrent” nghĩa là hỗ trợ nhiều consumer thread song song (concurrency).
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaProperties props,
            ConsumerFactory<String, Object> cf,
            CommonErrorHandler eh
    ) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        //gắn nó vào container factory để mọi listener tạo consumer theo đúng config starter.
        f.setConsumerFactory(cf);
        f.setBatchListener(props.getConsumer().isBatch());
        //concurrency hiệu quả tối đa ≈ số partitions của topic
        // (mỗi partition chỉ có 1 consumer trong group đọc tại một thời điểm).
        f.setConcurrency(props.getConsumer().getConcurrency());
        //AckMode.RECORD: xử lý theo từng dòng xong mỗi record thì commit record đó.
        //AckMode.BATCH: xử lý theo lô xong cả batch thì commit một lần.
        f.getContainerProperties().setAckMode(
                props.getConsumer().isBatch() ? ContainerProperties.AckMode.BATCH
                        : ContainerProperties.AckMode.RECORD
        );
        f.setCommonErrorHandler(eh);
        if (props.getObservability().isObservationEnabled()) {
            // Spring Kafka: ContainerProperties.setObservationEnabled(true) enables Observations/Tracing
            // NOTE: Observation spans do NOT support batch listener.
            if (props.getConsumer().isBatch() && props.getObservability().isWarnOnBatch()) {
                log.warn("lsf.kafka.consumer.batch=true but observationEnabled=true. " +
                        "Spring Kafka Observation does not support batch listener spans. " +
                        "Set lsf.kafka.consumer.batch=false if you want tracing spans per record.");
            }
            invokeIfPresent(f.getContainerProperties(), "setObservationEnabled", true);
        }

        return f;
    }

    private static void invokeIfPresent(Object target, String methodName, boolean arg) {
        Method m = ReflectionUtils.findMethod(target.getClass(), methodName, boolean.class);
        if (m == null) return;
        try {
            ReflectionUtils.makeAccessible(m);
            m.invoke(target, arg);
        } catch (Exception ex) {
            log.warn("Failed to call {}.{}({})", target.getClass().getSimpleName(), methodName, arg, ex);
        }
    }
}
