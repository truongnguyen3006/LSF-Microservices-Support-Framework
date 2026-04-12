package com.myorg.lsf.eventing.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.PayloadConverter;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LsfEnvelopeListenerTest {

    @AfterEach
    void tearDown() {
        LsfDispatchOutcome.clear();
    }

    @Test
    void shouldDispatchSingleConsumerRecordValue() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        LsfEnvelopeListener listener = new LsfEnvelopeListener(dispatcher, new TestPayloadConverter());

        listener.onMessage(new ConsumerRecord<>("orders.events", 0, 1L, "ORD-1", "payload-1"));

        assertThat(dispatcher.eventIds).containsExactly("payload-1");
        assertThat(LsfDispatchOutcome.consume()).isNull();
    }

    @Test
    void shouldDispatchAllRecordsFromConsumerRecordsBatchAndClearOutcome() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        LsfEnvelopeListener listener = new LsfEnvelopeListener(dispatcher, new TestPayloadConverter());
        TopicPartition partition = new TopicPartition("orders.events", 0);
        ConsumerRecords<String, Object> batch = new ConsumerRecords<>(Map.of(
                partition,
                List.of(
                        new ConsumerRecord<>("orders.events", 0, 1L, "ORD-1", "payload-1"),
                        new ConsumerRecord<>("orders.events", 0, 2L, "ORD-2", "payload-2")
                )
        ));

        listener.onMessage(batch);

        assertThat(dispatcher.eventIds).containsExactly("payload-1", "payload-2");
        assertThat(LsfDispatchOutcome.consume()).isNull();
    }

    static class RecordingDispatcher implements LsfDispatcher {

        private final List<String> eventIds = new ArrayList<>();

        @Override
        public void dispatch(EventEnvelope env) {
            eventIds.add(env.getEventId());
            LsfDispatchOutcome.markDuplicate();
        }
    }

    static class TestPayloadConverter implements PayloadConverter {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public EventEnvelope toEnvelope(Object payload) {
            return EventEnvelope.builder()
                    .eventId(String.valueOf(payload))
                    .eventType("orders.created.v1")
                    .payload(mapper.createObjectNode().put("value", String.valueOf(payload)))
                    .build();
        }
    }
}
