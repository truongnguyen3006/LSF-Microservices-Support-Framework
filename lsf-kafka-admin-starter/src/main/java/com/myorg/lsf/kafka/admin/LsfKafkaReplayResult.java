package com.myorg.lsf.kafka.admin;

import java.time.Instant;

public record LsfKafkaReplayResult(
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String targetTopic,
        String eventId,
        Instant replayedAt
) {
}
