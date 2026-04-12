package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

import java.util.concurrent.CompletableFuture;
//Giao diện để developer gửi sự kiện.
// Tự động bọc payload vào EventEnvelope (qua EnvelopeBuilder),
// đính kèm các Header quan trọng của Kafka (Event ID, Type, Correlation ID)
// để phục vụ cho việc tracking (truy vết) hệ thống.
public interface LsfPublisher {
    CompletableFuture<?> publish(String topic, String key, String eventType,  String aggregateId, Object payload);

    default CompletableFuture<?> publish(
            String topic,
            String key,
            String eventType,
            String aggregateId,
            Object payload,
            LsfPublishOptions options
    ) {
        return publish(topic, key, eventType, aggregateId, payload);
    }

    default CompletableFuture<?> publish(String topic, String key, EventEnvelope envelope) {
        throw new UnsupportedOperationException("publish(topic, key, envelope) is not supported by this publisher");
    }
}
