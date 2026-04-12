package com.myorg.lsf.template.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfPublishOptions;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.outbox.OutboxWriter;
import com.myorg.lsf.template.api.CreateWorkItemRequest;
import com.myorg.lsf.template.config.TemplateServiceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateIntegrationEventPublisherTest {

    @AfterEach
    void tearDown() {
        LsfRequestContextHolder.clear();
    }

    @Test
    void shouldAppendToOutboxWhenWriterExists() {
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        RecordingPublisher publisher = new RecordingPublisher();
        when(outboxWriter.append(any(EventEnvelope.class), eq("template.integration.events"), eq("work-1"))).thenReturn(11L);

        LsfRequestContextHolder.setContext(new LsfRequestContext("corr-1", "cause-1", "req-1"));

        TemplateIntegrationEventPublisher eventPublisher = new TemplateIntegrationEventPublisher(
                new ObjectMapper(),
                new MockEnvironment().withProperty("spring.application.name", "template-service"),
                publisher,
                objectProvider(outboxWriter),
                properties()
        );

        TemplatePublishResult result = eventPublisher.publishRequestedEvent(new CreateWorkItemRequest("work-1", "SYNC_REFERENCE"));

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(envelopeCaptor.capture(), eq("template.integration.events"), eq("work-1"));

        EventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(result.publicationMode()).isEqualTo("OUTBOX");
        assertThat(result.correlationId()).isEqualTo("corr-1");
        assertThat(result.requestId()).isEqualTo("req-1");
        assertThat(publisher.topic).isNull();
        assertThat(envelope.getEventType()).isEqualTo(TemplateEventTypes.RESOURCE_REQUESTED_V1);
        assertThat(envelope.getAggregateId()).isEqualTo("work-1");
        assertThat(envelope.getCorrelationId()).isEqualTo("corr-1");
        assertThat(envelope.getCausationId()).isEqualTo("cause-1");
        assertThat(envelope.getRequestId()).isEqualTo("req-1");
        assertThat(envelope.getProducer()).isEqualTo("template-service");
    }

    @Test
    void shouldFallbackToDirectPublisherWhenOutboxWriterMissing() {
        RecordingPublisher publisher = new RecordingPublisher();

        LsfRequestContextHolder.setContext(new LsfRequestContext("corr-2", "cause-2", "req-2"));

        TemplateIntegrationEventPublisher eventPublisher = new TemplateIntegrationEventPublisher(
                new ObjectMapper(),
                new MockEnvironment(),
                publisher,
                objectProvider(null),
                properties()
        );

        TemplatePublishResult result = eventPublisher.publishRequestedEvent(new CreateWorkItemRequest("work-2", "SEND_EVENT"));

        assertThat(result.publicationMode()).isEqualTo("DIRECT");
        assertThat(result.correlationId()).isEqualTo("corr-2");
        assertThat(result.requestId()).isEqualTo("req-2");
        assertThat(publisher.topic).isEqualTo("template.integration.events");
        assertThat(publisher.key).isEqualTo("work-2");
        assertThat(publisher.eventType).isEqualTo(TemplateEventTypes.RESOURCE_REQUESTED_V1);
        assertThat(publisher.aggregateId).isEqualTo("work-2");
        assertThat(publisher.options.getCorrelationId()).isEqualTo("corr-2");
        assertThat(publisher.options.getCausationId()).isEqualTo("cause-2");
        assertThat(publisher.options.getRequestId()).isEqualTo("req-2");
    }

    private TemplateServiceProperties properties() {
        return new TemplateServiceProperties();
    }

    private ObjectProvider<OutboxWriter> objectProvider(OutboxWriter outboxWriter) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (outboxWriter != null) {
            beanFactory.registerSingleton("outboxWriter", outboxWriter);
        }
        return beanFactory.getBeanProvider(OutboxWriter.class);
    }

    private static final class RecordingPublisher implements LsfPublisher {
        private String topic;
        private String key;
        private String eventType;
        private String aggregateId;
        private Object payload;
        private LsfPublishOptions options;

        @Override
        public CompletableFuture<?> publish(String topic, String key, String eventType, String aggregateId, Object payload) {
            this.topic = topic;
            this.key = key;
            this.eventType = eventType;
            this.aggregateId = aggregateId;
            this.payload = payload;
            this.options = null;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> publish(String topic,
                                            String key,
                                            String eventType,
                                            String aggregateId,
                                            Object payload,
                                            LsfPublishOptions options) {
            this.topic = topic;
            this.key = key;
            this.eventType = eventType;
            this.aggregateId = aggregateId;
            this.payload = payload;
            this.options = options;
            return CompletableFuture.completedFuture(null);
        }
    }
}
