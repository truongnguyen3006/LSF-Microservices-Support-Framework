package com.myorg.lsf.contracts.core.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class EnvelopeBuilder {
    //chuẩn hóa event: mọi event đều có metadata giống nhau
    public static EventEnvelope wrap(ObjectMapper mapper,
                                     String eventType,
                                     int version,
                                     String aggregateId,
                                     String correlationId,
                                     String causationId,
                                     String producer,
                                     Object payloadObj
                                     ){
        return wrap(
                mapper,
                eventType,
                version,
                aggregateId,
                correlationId,
                causationId,
                null,
                producer,
                payloadObj
        );
    }

    public static EventEnvelope wrap(ObjectMapper mapper,
                                     String eventType,
                                     int version,
                                     String aggregateId,
                                     String correlationId,
                                     String causationId,
                                     String requestId,
                                     String producer,
                                     Object payloadObj
                                     ){
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .version(version)
                .aggregateId(aggregateId)
                .correlationId(correlationId)
                .causationId(causationId)
                .requestId(requestId)
                .occurredAtMs(System.currentTimeMillis())
                .producer(producer)
                .payload(mapper.valueToTree(payloadObj))
                .build();
    }
}
