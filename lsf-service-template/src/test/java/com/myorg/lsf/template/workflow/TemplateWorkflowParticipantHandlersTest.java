package com.myorg.lsf.template.workflow;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfPublishOptions;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.template.config.TemplateServiceProperties;
import com.myorg.lsf.template.messaging.TemplateEventTypes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateWorkflowParticipantHandlersTest {

    @Test
    void shouldPublishReplyWithSagaMetadataPreserved() {
        RecordingPublisher publisher = new RecordingPublisher();

        TemplateServiceProperties properties = new TemplateServiceProperties();
        TemplateWorkflowParticipantHandlers handlers = new TemplateWorkflowParticipantHandlers(publisher, properties);

        EventEnvelope envelope = EventEnvelope.builder()
                .eventId("evt-1")
                .eventType(TemplateEventTypes.WORKFLOW_STEP_REQUESTED_V1)
                .aggregateId("workflow-1")
                .correlationId("corr-1")
                .requestId("req-1")
                .build();

        handlers.onStepRequested(envelope, new TemplateWorkflowStepRequested("workflow-1", "reserveDependency"));

        assertThat(publisher.topic).isEqualTo("template.workflow.replies");
        assertThat(publisher.key).isEqualTo("workflow-1");
        assertThat(publisher.eventType).isEqualTo(TemplateEventTypes.WORKFLOW_STEP_COMPLETED_V1);
        assertThat(publisher.aggregateId).isEqualTo("workflow-1");
        assertThat(publisher.payload).isInstanceOf(TemplateWorkflowStepCompleted.class);
        assertThat(publisher.options.getCorrelationId()).isEqualTo("corr-1");
        assertThat(publisher.options.getCausationId()).isEqualTo("evt-1");
        assertThat(publisher.options.getRequestId()).isEqualTo("req-1");
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
