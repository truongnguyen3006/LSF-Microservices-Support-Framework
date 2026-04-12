package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAwareLsfDispatcherTest {

    @AfterEach
    void tearDown() {
        LsfRequestContextHolder.clear();
        LsfTraceContextHolder.clear();
    }

    @Test
    void shouldInstallRequestAndTraceContextDuringDispatch() {
        AtomicReference<String> correlationId = new AtomicReference<>();
        AtomicReference<String> traceparent = new AtomicReference<>();

        ContextAwareLsfDispatcher dispatcher = new ContextAwareLsfDispatcher(env -> {
            correlationId.set(LsfRequestContextHolder.getContext().correlationId());
            traceparent.set(LsfTraceContextHolder.getContext().header(CoreHeaders.TRACEPARENT));
        });

        EventEnvelope envelope = EventEnvelope.builder()
                .eventId("evt-1")
                .eventType("demo.event.v1")
                .correlationId("corr-1")
                .causationId("cause-1")
                .requestId("req-1")
                .traceHeaders(Map.of(
                        CoreHeaders.TRACEPARENT, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
                ))
                .payload(new ObjectMapper().createObjectNode().put("hello", "world"))
                .build();

        dispatcher.dispatch(envelope);

        assertThat(correlationId.get()).isEqualTo("corr-1");
        assertThat(traceparent.get()).isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        assertThat(LsfRequestContextHolder.getContext()).isNull();
        assertThat(LsfTraceContextHolder.getContext()).isNull();
    }
}
