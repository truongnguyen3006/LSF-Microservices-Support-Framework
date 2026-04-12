package com.myorg.lsf.kafka.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.kafka.LsfDlqHeaders;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class LsfKafkaDlqService {

    private final AdminClient adminClient;
    private final ConsumerFactory<String, Object> consumerFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LsfKafkaAdminProperties properties;
    private final LsfKafkaReplayMetrics metrics;

    public List<String> listDlqTopics() {
        try {
            return adminClient.listTopics().names().get(5, TimeUnit.SECONDS).stream()
                    .filter(topic -> topic.endsWith(properties.getDlqSuffix()))
                    .sorted()
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to list DLQ topics", ex);
        }
    }

    public List<LsfKafkaDlqRecordView> listRecords(String topic, Integer partition, Integer limit, Long beforeOffset) {
        validateTopic(topic);
        if (beforeOffset != null && partition == null) {
            throw new IllegalArgumentException("beforeOffset requires partition to be specified.");
        }

        int effectiveLimit = clamp(limit != null ? limit : properties.getDefaultLimit());
        try (Consumer<String, Object> consumer = createConsumer()) {
            List<TopicPartition> partitions = resolveTopicPartitions(consumer, topic, partition);
            consumer.assign(partitions);

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            for (TopicPartition topicPartition : partitions) {
                long upperExclusive = endOffsets.getOrDefault(topicPartition, 0L);
                if (partition != null && beforeOffset != null && topicPartition.partition() == partition) {
                    upperExclusive = Math.min(upperExclusive, beforeOffset);
                }
                long startOffset = Math.max(beginningOffsets.getOrDefault(topicPartition, 0L), upperExclusive - effectiveLimit);
                consumer.seek(topicPartition, startOffset);
            }

            List<ConsumerRecord<String, Object>> records = new ArrayList<>();
            long deadline = System.nanoTime() + properties.getPollTimeout().toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, Object> polled = consumer.poll(java.time.Duration.ofMillis(200));
                if (polled.isEmpty()) {
                    break;
                }
                boolean added = false;
                for (ConsumerRecord<String, Object> record : polled) {
                    if (partition != null && beforeOffset != null && record.offset() >= beforeOffset) {
                        continue;
                    }
                    records.add(record);
                    added = true;
                }
                if (!added) {
                    break;
                }
            }

            return records.stream()
                    .sorted(recordComparator().reversed())
                    .limit(effectiveLimit)
                    .map(this::toView)
                    .toList();
        }
    }

    public LsfKafkaDlqRecordView getRecord(String topic, int partition, long offset) {
        try (Consumer<String, Object> consumer = createConsumer()) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            long deadline = System.nanoTime() + properties.getPollTimeout().toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, Object> polled = consumer.poll(java.time.Duration.ofMillis(200));
                for (ConsumerRecord<String, Object> record : polled.records(topicPartition)) {
                    if (record.offset() == offset) {
                        return toView(record);
                    }
                    if (record.offset() > offset) {
                        break;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No DLQ record found for topic=" + topic + ", partition=" + partition + ", offset=" + offset);
    }

    public LsfKafkaReplayResult replay(LsfKafkaReplayRequest request) {
        if (!properties.isAllowReplay()) {
            throw new IllegalStateException("Replay is disabled. Set lsf.kafka.admin.allow-replay=true to enable.");
        }
        if (request == null || request.topic() == null || request.partition() == null || request.offset() == null) {
            throw new IllegalArgumentException("topic, partition, and offset are required.");
        }

        ConsumerRecord<String, Object> sourceRecord = getConsumerRecord(request.topic(), request.partition(), request.offset());
        String targetTopic = resolveTargetTopic(request.topic(), request.targetTopic());
        boolean retainDlqHeaders = Boolean.TRUE.equals(request.retainDlqHeaders());
        Instant replayedAt = Instant.now();

        try {
            ProducerRecord<String, Object> replayRecord = new ProducerRecord<>(targetTopic, sourceRecord.key(), sourceRecord.value());
            copyHeaders(sourceRecord.headers(), replayRecord.headers(), retainDlqHeaders);
            addReplayHeaders(replayRecord.headers(), sourceRecord, replayedAt);

            kafkaTemplate.send(replayRecord).get(10, TimeUnit.SECONDS);
            if (metrics != null) {
                metrics.incSuccess(sourceRecord.topic(), targetTopic);
            }
            return new LsfKafkaReplayResult(
                    sourceRecord.topic(),
                    sourceRecord.partition(),
                    sourceRecord.offset(),
                    targetTopic,
                    headerValue(sourceRecord.headers(), com.myorg.lsf.contracts.core.conventions.CoreHeaders.EVENT_ID),
                    replayedAt
            );
        } catch (Exception ex) {
            if (metrics != null) {
                metrics.incFail(sourceRecord.topic(), targetTopic);
            }
            throw new IllegalStateException("Failed to replay DLQ record topic=" + sourceRecord.topic()
                    + ", partition=" + sourceRecord.partition()
                    + ", offset=" + sourceRecord.offset(), ex);
        }
    }

    private ConsumerRecord<String, Object> getConsumerRecord(String topic, int partition, long offset) {
        try (Consumer<String, Object> consumer = createConsumer()) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            long deadline = System.nanoTime() + properties.getPollTimeout().toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, Object> polled = consumer.poll(java.time.Duration.ofMillis(200));
                for (ConsumerRecord<String, Object> record : polled.records(topicPartition)) {
                    if (record.offset() == offset) {
                        return record;
                    }
                    if (record.offset() > offset) {
                        break;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No DLQ record found for replay topic=" + topic + ", partition=" + partition + ", offset=" + offset);
    }

    private Consumer<String, Object> createConsumer() {
        return consumerFactory.createConsumer("lsf-kafka-admin", "lsf-kafka-admin-" + UUID.randomUUID());
    }

    private List<TopicPartition> resolveTopicPartitions(Consumer<String, Object> consumer, String topic, Integer partition) {
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .sorted(Comparator.comparingInt(TopicPartition::partition))
                .toList();
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException("Topic does not exist or has no partitions: " + topic);
        }
        if (partition == null) {
            return partitions;
        }
        return partitions.stream()
                .filter(tp -> tp.partition() == partition)
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new IllegalArgumentException("Partition " + partition + " not found for topic=" + topic));
    }

    private LsfKafkaDlqRecordView toView(ConsumerRecord<String, Object> record) {
        EventEnvelope envelope = tryEnvelope(record.value());
        Map<String, String> headers = extractHeaders(record.headers());
        return new LsfKafkaDlqRecordView(
                record.topic(),
                record.partition(),
                record.offset(),
                Instant.ofEpochMilli(record.timestamp()),
                record.key(),
                firstNonBlank(headers.get(LsfDlqHeaders.ORIGINAL_TOPIC), stripDlqSuffix(record.topic())),
                parseInteger(headers.get(LsfDlqHeaders.ORIGINAL_PARTITION)),
                parseLong(headers.get(LsfDlqHeaders.ORIGINAL_OFFSET)),
                envelope == null ? null : envelope.getEventId(),
                envelope == null ? null : envelope.getEventType(),
                envelope == null ? null : envelope.getCorrelationId(),
                envelope == null ? null : envelope.getCausationId(),
                envelope == null ? null : envelope.getRequestId(),
                envelope == null ? null : envelope.getProducer(),
                headers.get(LsfDlqHeaders.REASON),
                Boolean.parseBoolean(headers.getOrDefault(LsfDlqHeaders.NON_RETRYABLE, "false")),
                headers.get(LsfDlqHeaders.EXCEPTION_CLASS),
                headers.get(LsfDlqHeaders.EXCEPTION_MESSAGE),
                headers,
                toJson(record.value())
        );
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (value instanceof byte[] bytes) {
            try {
                return objectMapper.readTree(bytes);
            } catch (Exception ex) {
                return TextNode.valueOf(java.util.Base64.getEncoder().encodeToString(bytes));
            }
        }
        if (value instanceof String string) {
            try {
                return objectMapper.readTree(string);
            } catch (Exception ex) {
                return TextNode.valueOf(string);
            }
        }
        return objectMapper.valueToTree(value);
    }

    private EventEnvelope tryEnvelope(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof EventEnvelope eventEnvelope) {
            return eventEnvelope;
        }
        try {
            if (value instanceof String string) {
                return objectMapper.readValue(string, EventEnvelope.class);
            }
            return objectMapper.convertValue(value, EventEnvelope.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, String> extractHeaders(Headers headers) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Header header : headers) {
            values.put(header.key(), header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8));
        }
        return values;
    }

    private void copyHeaders(Headers source, Headers target, boolean retainDlqHeaders) {
        for (Header header : source) {
            if (!retainDlqHeaders && header.key().startsWith("lsf.dlq.")) {
                continue;
            }
            target.add(new RecordHeader(header.key(), header.value()));
        }
    }

    private void addReplayHeaders(Headers headers, ConsumerRecord<String, Object> sourceRecord, Instant replayedAt) {
        addHeader(headers, LsfKafkaReplayHeaders.SOURCE_TOPIC, sourceRecord.topic());
        addHeader(headers, LsfKafkaReplayHeaders.SOURCE_PARTITION, String.valueOf(sourceRecord.partition()));
        addHeader(headers, LsfKafkaReplayHeaders.SOURCE_OFFSET, String.valueOf(sourceRecord.offset()));
        addHeader(headers, LsfKafkaReplayHeaders.REPLAYED_AT, replayedAt.toString());
    }

    private void addHeader(Headers headers, String key, String value) {
        headers.remove(key);
        headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }

    private String resolveTargetTopic(String sourceTopic, String requestedTargetTopic) {
        if (StringUtils.hasText(requestedTargetTopic)) {
            return requestedTargetTopic.trim();
        }
        String stripped = stripDlqSuffix(sourceTopic);
        if (sourceTopic.equals(stripped)) {
            throw new IllegalArgumentException("targetTopic is required when source topic does not end with " + properties.getDlqSuffix());
        }
        return stripped;
    }

    private String stripDlqSuffix(String topic) {
        if (topic == null || !topic.endsWith(properties.getDlqSuffix())) {
            return topic;
        }
        return topic.substring(0, topic.length() - properties.getDlqSuffix().length());
    }

    private void validateTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
    }

    private int clamp(int value) {
        if (value <= 0) {
            return properties.getDefaultLimit();
        }
        return Math.min(value, properties.getMaxLimit());
    }

    private Comparator<ConsumerRecord<String, Object>> recordComparator() {
        return Comparator
                .comparingLong(ConsumerRecord<String, Object>::timestamp)
                .thenComparingInt(ConsumerRecord<String, Object>::partition)
                .thenComparingLong(ConsumerRecord<String, Object>::offset);
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.parseLong(value);
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String headerValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
