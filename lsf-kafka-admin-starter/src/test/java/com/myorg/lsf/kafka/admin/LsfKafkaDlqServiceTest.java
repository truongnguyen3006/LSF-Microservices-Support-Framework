package com.myorg.lsf.kafka.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.kafka.LsfDlqHeaders;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LsfKafkaDlqServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldListRecentDlqRecordsWithOriginalMetadata() {
        MockConsumer<String, Object> consumer = preparedConsumer();
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        when(consumerFactory.createConsumer(any(String.class), any(String.class))).thenReturn(consumer);

        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        AdminClient adminClient = mock(AdminClient.class);
        LsfKafkaAdminProperties properties = new LsfKafkaAdminProperties();

        LsfKafkaDlqService service = new LsfKafkaDlqService(
                adminClient,
                consumerFactory,
                kafkaTemplate,
                new ObjectMapper(),
                properties,
                null
        );

        List<LsfKafkaDlqRecordView> records = service.listRecords("orders.DLQ", 0, 10, null);

        assertThat(records).hasSize(1);
        LsfKafkaDlqRecordView record = records.getFirst();
        assertThat(record.originalTopic()).isEqualTo("orders");
        assertThat(record.originalPartition()).isEqualTo(0);
        assertThat(record.originalOffset()).isEqualTo(15L);
        assertThat(record.eventId()).isEqualTo("evt-15");
        assertThat(record.reason()).isEqualTo("NON_RETRYABLE");
        assertThat(record.nonRetryable()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReplayDlqRecordToSourceTopicAndStripDlqHeadersByDefault() throws Exception {
        MockConsumer<String, Object> consumer = preparedConsumer();
        ConsumerFactory<String, Object> consumerFactory = mock(ConsumerFactory.class);
        when(consumerFactory.createConsumer(any(String.class), any(String.class))).thenReturn(consumer);

        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        AdminClient adminClient = mock(AdminClient.class);
        LsfKafkaAdminProperties properties = new LsfKafkaAdminProperties();

        LsfKafkaDlqService service = new LsfKafkaDlqService(
                adminClient,
                consumerFactory,
                kafkaTemplate,
                new ObjectMapper(),
                properties,
                null
        );

        LsfKafkaReplayResult result = service.replay(new LsfKafkaReplayRequest("orders.DLQ", 0, 15L, null, false));

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> replayRecord = captor.getValue();
        assertThat(result.targetTopic()).isEqualTo("orders");
        assertThat(replayRecord.topic()).isEqualTo("orders");
        assertThat(headerValue(replayRecord, LsfKafkaReplayHeaders.SOURCE_TOPIC)).isEqualTo("orders.DLQ");
        assertThat(headerValue(replayRecord, LsfKafkaReplayHeaders.SOURCE_OFFSET)).isEqualTo("15");
        assertThat(headerValue(replayRecord, LsfDlqHeaders.REASON)).isNull();
        assertThat(headerValue(replayRecord, CoreHeaders.EVENT_ID)).isEqualTo("evt-15");
    }

    private MockConsumer<String, Object> preparedConsumer() {
        MockConsumer<String, Object> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition topicPartition = new TopicPartition("orders.DLQ", 0);
        consumer.updatePartitions("orders.DLQ", List.of(new PartitionInfo("orders.DLQ", 0, null, null, null)));
        consumer.assign(List.of(topicPartition));
        consumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        consumer.updateEndOffsets(Map.of(topicPartition, 16L));

        EventEnvelope envelope = EventEnvelope.builder()
                .eventId("evt-15")
                .eventType("orders.failed.v1")
                .correlationId("corr-15")
                .requestId("req-15")
                .producer("orders-service")
                .payload(new ObjectMapper().createObjectNode().put("orderId", "ORD-15"))
                .build();

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("orders.DLQ", 0, 15L, "ORD-15", envelope);
        record.headers().add(new RecordHeader(LsfDlqHeaders.REASON, bytes("NON_RETRYABLE")));
        record.headers().add(new RecordHeader(LsfDlqHeaders.NON_RETRYABLE, bytes("true")));
        record.headers().add(new RecordHeader(LsfDlqHeaders.ORIGINAL_TOPIC, bytes("orders")));
        record.headers().add(new RecordHeader(LsfDlqHeaders.ORIGINAL_PARTITION, bytes("0")));
        record.headers().add(new RecordHeader(LsfDlqHeaders.ORIGINAL_OFFSET, bytes("15")));
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_ID, bytes("evt-15")));
        consumer.addRecord(record);
        return consumer;
    }

    private static String headerValue(ProducerRecord<String, Object> record, String key) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
