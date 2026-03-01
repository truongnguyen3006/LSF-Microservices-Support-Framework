package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EnvelopeBuilder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
//lớp cài đặt để developer gửi sự kiện.
// Tự động bọc payload vào EventEnvelope (qua EnvelopeBuilder),
// đính kèm các Header quan trọng của Kafka (Event ID, Type, Correlation ID)
// để phục vụ cho việc tracking (truy vết) hệ thống.
@Data
@AllArgsConstructor
public class DefaultLsfPublisher implements LsfPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public final ObjectMapper mapper;
    public final String producerName;

    @Override
    public CompletableFuture<?> publish(String topic, String key, String eventType, String aggregateId, Object payload) {
        String correlationId = StringUtils.hasText(aggregateId) ? aggregateId : key;

        EventEnvelope env = EnvelopeBuilder.wrap(
                mapper,
                eventType,
                1,
                aggregateId,
                correlationId,
                null,
                producerName,
                payload
        );

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, env);

        // headers giúp observability/debug (tuỳ bạn dùng hay không)
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_ID, env.getEventId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_TYPE, env.getEventType().getBytes(StandardCharsets.UTF_8)));
        if (StringUtils.hasText(env.getCorrelationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CORRELATION_ID, env.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
        }
        if (StringUtils.hasText(env.getCausationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CAUSATION_ID, env.getCausationId().getBytes(StandardCharsets.UTF_8)));
        }

        return kafkaTemplate.send(record);
    }
}
