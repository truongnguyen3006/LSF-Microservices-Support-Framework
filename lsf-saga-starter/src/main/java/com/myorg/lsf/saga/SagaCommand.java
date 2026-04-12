package com.myorg.lsf.saga;

import org.springframework.util.StringUtils;

public record SagaCommand(
        String topic,
        String key,
        String eventType,
        String aggregateId,
        Object payload
) {
    public SagaCommand {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (!StringUtils.hasText(eventType)) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
    }

    public String effectiveKey(String sagaId) {
        return StringUtils.hasText(key) ? key : sagaId;
    }

    public String effectiveAggregateId(String sagaId) {
        return StringUtils.hasText(aggregateId) ? aggregateId : sagaId;
    }
}
