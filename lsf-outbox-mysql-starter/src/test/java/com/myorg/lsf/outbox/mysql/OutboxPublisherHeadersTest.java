package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherHeadersTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldRestoreCoreAndTraceHeadersWhenPublishingOutboxRow() throws Exception {
        LsfOutboxMySqlProperties properties = new LsfOutboxMySqlProperties();
        properties.setEnabled(true);
        properties.getPublisher().setEnabled(true);
        properties.getPublisher().setBatchSize(1);

        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        when(repository.claimBatch(any(), any(), any(), any(Integer.class))).thenReturn(1);
        when(repository.findClaimed(any(), any(), any(Integer.class))).thenReturn(List.of(
                new OutboxRow(1L, "orders.events", "ORD-1", "evt-1", envelopeJson(), 0)
        ));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null)
        );

        OutboxPublisher publisher = new OutboxPublisher(
                properties,
                repository,
                kafkaTemplate,
                new ObjectMapper(),
                transactionTemplate,
                Clock.fixed(Instant.parse("2026-04-06T00:00:00Z"), ZoneOffset.UTC),
                new OutboxPublisherHooks() {},
                null
        );

        publisher.runOnce();

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> record = captor.getValue();

        assertThat(headerValue(record, CoreHeaders.EVENT_ID)).isEqualTo("evt-1");
        assertThat(headerValue(record, CoreHeaders.CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(headerValue(record, CoreHeaders.REQUEST_ID)).isEqualTo("req-1");
        assertThat(headerValue(record, CoreHeaders.TRACEPARENT))
                .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
    }

    private String envelopeJson() throws Exception {
        EventEnvelope envelope = EventEnvelope.builder()
                .eventId("evt-1")
                .eventType("orders.created.v1")
                .correlationId("corr-1")
                .requestId("req-1")
                .traceHeaders(Map.of(
                        CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
                ))
                .payload(new ObjectMapper().createObjectNode().put("orderId", "ORD-1"))
                .build();
        return new ObjectMapper().writeValueAsString(envelope);
    }

    private static String headerValue(ProducerRecord<String, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
