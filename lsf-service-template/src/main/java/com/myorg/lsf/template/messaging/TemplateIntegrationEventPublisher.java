package com.myorg.lsf.template.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EnvelopeBuilder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfPublishOptions;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.outbox.OutboxWriter;
import com.myorg.lsf.template.api.CreateWorkItemRequest;
import com.myorg.lsf.template.config.TemplateServiceProperties;
import com.myorg.lsf.template.support.TemplateRequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateIntegrationEventPublisher {

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final LsfPublisher publisher;
    private final ObjectProvider<OutboxWriter> outboxWriterProvider;
    private final TemplateServiceProperties properties;

    @Transactional
    public TemplatePublishResult publishRequestedEvent(CreateWorkItemRequest request) {
        TemplateRequestMetadata metadata = TemplateRequestMetadata.capture(request.workItemId());
        TemplateWorkItemRequested payload = new TemplateWorkItemRequested(request.workItemId(), request.operation());

        OutboxWriter outboxWriter = outboxWriterProvider.getIfAvailable();
        if (outboxWriter != null) {
            // In a real service, keep repository writes and outbox append in the same transaction.
            EventEnvelope envelope = EnvelopeBuilder.wrap(
                    objectMapper,
                    properties.getIntegrationEventType(),
                    1,
                    request.workItemId(),
                    metadata.correlationId(),
                    metadata.causationId(),
                    metadata.requestId(),
                    environment.getProperty("spring.application.name", "template-service"),
                    payload
            );
            outboxWriter.append(envelope, properties.getIntegrationTopic(), request.workItemId());
            return new TemplatePublishResult("OUTBOX", metadata.correlationId(), metadata.requestId());
        }

        publisher.publish(
                properties.getIntegrationTopic(),
                request.workItemId(),
                properties.getIntegrationEventType(),
                request.workItemId(),
                payload,
                LsfPublishOptions.builder()
                        .correlationId(metadata.correlationId())
                        .causationId(metadata.causationId())
                        .requestId(metadata.requestId())
                        .build()
        );
        return new TemplatePublishResult("DIRECT", metadata.correlationId(), metadata.requestId());
    }
}
