package com.myorg.lsf.kafka.admin;

public record LsfKafkaReplayRequest(
        String topic,
        Integer partition,
        Long offset,
        String targetTopic,
        Boolean retainDlqHeaders
) {
}
