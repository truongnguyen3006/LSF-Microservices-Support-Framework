package com.myorg.lsf.kafka.admin;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

public record LsfKafkaDlqRecordView(
        String topic,
        int partition,
        long offset,
        Instant timestamp,
        String key,
        String originalTopic,
        Integer originalPartition,
        Long originalOffset,
        String eventId,
        String eventType,
        String correlationId,
        String causationId,
        String requestId,
        String producer,
        String reason,
        boolean nonRetryable,
        String exceptionClass,
        String exceptionMessage,
        Map<String, String> headers,
        JsonNode value
) {
}
