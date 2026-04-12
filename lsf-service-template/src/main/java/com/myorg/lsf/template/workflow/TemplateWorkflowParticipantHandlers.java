package com.myorg.lsf.template.workflow;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import com.myorg.lsf.eventing.LsfPublishOptions;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.template.config.TemplateServiceProperties;
import com.myorg.lsf.template.messaging.TemplateEventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateWorkflowParticipantHandlers {

    private final LsfPublisher publisher;
    private final TemplateServiceProperties properties;

    @LsfEventHandler(value = TemplateEventTypes.WORKFLOW_STEP_REQUESTED_V1, payload = TemplateWorkflowStepRequested.class)
    public void onStepRequested(EventEnvelope envelope, TemplateWorkflowStepRequested payload) {
        String correlationId = resolveCorrelationId(envelope, payload.workflowId());
        TemplateWorkflowStepCompleted reply = new TemplateWorkflowStepCompleted(
                payload.workflowId(),
                payload.stepName(),
                "COMPLETED"
        );

        publisher.publish(
                properties.getWorkflowReplyTopic(),
                payload.workflowId(),
                properties.getWorkflowCompletedEventType(),
                payload.workflowId(),
                reply,
                LsfPublishOptions.builder()
                        .correlationId(correlationId)
                        .causationId(envelope.getEventId())
                        .requestId(envelope.getRequestId())
                        .build()
        );

        log.info(
                "Published workflow reply. correlationId={}, causationId={}, requestId={}, workflowId={}, stepName={}",
                correlationId,
                envelope.getEventId(),
                envelope.getRequestId(),
                payload.workflowId(),
                payload.stepName()
        );
    }

    private String resolveCorrelationId(EventEnvelope envelope, String fallbackCorrelationId) {
        if (StringUtils.hasText(envelope.getCorrelationId())) {
            return envelope.getCorrelationId();
        }
        return fallbackCorrelationId;
    }
}
