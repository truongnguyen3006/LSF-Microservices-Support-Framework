package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLsfPublisherTest {

    @AfterEach
    void tearDown() {
        LsfTraceContextHolder.clear();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPublishEnvelopeWithExplicitMetadata() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        DefaultLsfPublisher publisher = new DefaultLsfPublisher(kafkaTemplate, new ObjectMapper(), "default-producer");
        publisher.publish(
                "payment-events",
                "ORD-777",
                "payment.charged.v1",
                "ORD-777",
                new PaymentCharged("ORD-777"),
                LsfPublishOptions.builder()
                        .correlationId("corr-777")
                        .causationId("evt-command-777")
                        .requestId("req-777")
                        .producer("payment-service")
                        .build()
        );

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        EventEnvelope envelope = (EventEnvelope) record.value();

        assertThat(envelope.getCorrelationId()).isEqualTo("corr-777");
        assertThat(envelope.getCausationId()).isEqualTo("evt-command-777");
        assertThat(envelope.getRequestId()).isEqualTo("req-777");
        assertThat(envelope.getProducer()).isEqualTo("payment-service");
        assertThat(headerValue(record, CoreHeaders.CORRELATION_ID)).isEqualTo("corr-777");
        assertThat(headerValue(record, CoreHeaders.CAUSATION_ID)).isEqualTo("evt-command-777");
        assertThat(headerValue(record, CoreHeaders.REQUEST_ID)).isEqualTo("req-777");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCaptureTraceHeadersIntoEnvelopeAndKafkaRecord() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        LsfTraceContextHolder.setContext(new LsfTraceContext(Map.of(
                CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )));

        DefaultLsfPublisher publisher = new DefaultLsfPublisher(kafkaTemplate, new ObjectMapper(), "default-producer");
        publisher.publish(
                "payment-events",
                "ORD-999",
                "payment.requested.v1",
                "ORD-999",
                new PaymentCharged("ORD-999")
        );

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        EventEnvelope envelope = (EventEnvelope) record.value();

        assertThat(envelope.getTraceHeaders()).containsEntry(
                CoreHeaders.TRACEPARENT,
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        );
        assertThat(headerValue(record, CoreHeaders.TRACEPARENT))
                .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
    }

    private static String headerValue(ProducerRecord<String, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private record PaymentCharged(String orderId) {
    }
}
