package com.myorg.lsf.kafka.admin;

public final class LsfKafkaReplayHeaders {

    private LsfKafkaReplayHeaders() {
    }

    public static final String SOURCE_TOPIC = "lsf.replay.source.topic";
    public static final String SOURCE_PARTITION = "lsf.replay.source.partition";
    public static final String SOURCE_OFFSET = "lsf.replay.source.offset";
    public static final String REPLAYED_AT = "lsf.replay.replayed_at";
}
