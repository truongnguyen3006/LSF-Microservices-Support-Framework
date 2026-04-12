package com.myorg.lsf.kafka.admin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public class LsfKafkaReplayMetrics {

    private final MeterRegistry registry;
    private final String service;

    public LsfKafkaReplayMetrics(MeterRegistry registry, String service) {
        this.registry = registry;
        this.service = service;
    }

    public void preRegister() {
        Counter.builder("lsf.kafka.replay.success").tag("service", service).register(registry);
        Counter.builder("lsf.kafka.replay.fail").tag("service", service).register(registry);
    }

    public void incSuccess(String sourceTopic, String targetTopic) {
        Counter.builder("lsf.kafka.replay.success")
                .tags(Tags.of("service", service, "source_topic", sourceTopic, "target_topic", targetTopic))
                .register(registry)
                .increment();
    }

    public void incFail(String sourceTopic, String targetTopic) {
        Counter.builder("lsf.kafka.replay.fail")
                .tags(Tags.of("service", service, "source_topic", sourceTopic, "target_topic", targetTopic))
                .register(registry)
                .increment();
    }
}
