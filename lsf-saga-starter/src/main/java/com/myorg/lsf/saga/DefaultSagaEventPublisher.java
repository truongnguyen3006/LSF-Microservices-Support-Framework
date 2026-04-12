package com.myorg.lsf.saga;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfTraceHeaders;
import com.myorg.lsf.outbox.OutboxWriter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

public class DefaultSagaEventPublisher implements SagaEventPublisher {

    private final ObjectProvider<OutboxWriter> outboxWriterProvider;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final LsfSagaProperties properties;

    public DefaultSagaEventPublisher(
            ObjectProvider<OutboxWriter> outboxWriterProvider,
            ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
            LsfSagaProperties properties
    ) {
        this.outboxWriterProvider = outboxWriterProvider;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.properties = properties;
    }

    @Override
    public boolean isTransactional() {
        return resolveTransport() == SagaTransportMode.OUTBOX;
    }

    @Override
    public void publish(String topic, String key, EventEnvelope envelope) {
        LsfTraceHeaders.enrichEnvelope(envelope);
        SagaTransportMode mode = resolveTransport();
        if (mode == SagaTransportMode.OUTBOX) {
            OutboxWriter writer = outboxWriterProvider.getIfAvailable();
            if (writer == null) {
                throw new IllegalStateException("Saga transport OUTBOX requested but no OutboxWriter bean is available.");
            }
            writer.append(envelope, topic, key);
            return;
        }

        KafkaTemplate<String, Object> template = kafkaTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("Saga transport DIRECT requested but no KafkaTemplate bean is available.");
        }

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, envelope);
        addHeaders(record, envelope);
        template.send(record).join();
    }

    private SagaTransportMode resolveTransport() {
        SagaTransportMode mode = properties.getTransport().getMode();
        if (mode == SagaTransportMode.OUTBOX) {
            return SagaTransportMode.OUTBOX;
        }
        if (mode == SagaTransportMode.DIRECT) {
            return SagaTransportMode.DIRECT;
        }
        if (outboxWriterProvider.getIfAvailable() != null) {
            return SagaTransportMode.OUTBOX;
        }
        if (kafkaTemplateProvider.getIfAvailable() != null) {
            return SagaTransportMode.DIRECT;
        }
        throw new IllegalStateException(
                "No saga transport available. Provide OutboxWriter or KafkaTemplate, or disable lsf.saga."
        );
    }

    private void addHeaders(ProducerRecord<String, Object> record, EventEnvelope envelope) {
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_ID, bytes(envelope.getEventId())));
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_TYPE, bytes(envelope.getEventType())));
        if (StringUtils.hasText(envelope.getCorrelationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CORRELATION_ID, bytes(envelope.getCorrelationId())));
        }
        if (StringUtils.hasText(envelope.getCausationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CAUSATION_ID, bytes(envelope.getCausationId())));
        }
        if (StringUtils.hasText(envelope.getRequestId())) {
            record.headers().add(new RecordHeader(CoreHeaders.REQUEST_ID, bytes(envelope.getRequestId())));
        }
        LsfTraceHeaders.writeToKafkaHeaders(record.headers(), envelope);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
