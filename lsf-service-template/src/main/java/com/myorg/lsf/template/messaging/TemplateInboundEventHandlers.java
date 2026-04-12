package com.myorg.lsf.template.messaging;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TemplateInboundEventHandlers {

    @LsfEventHandler(value = TemplateEventTypes.REFERENCE_UPDATED_V1, payload = TemplateReferenceUpdated.class)
    public void onReferenceUpdated(EventEnvelope envelope, TemplateReferenceUpdated payload) {
        log.info(
                "Handled template event. eventType={}, aggregateId={}, correlationId={}, referenceId={}, status={}",
                envelope.getEventType(),
                envelope.getAggregateId(),
                envelope.getCorrelationId(),
                payload.referenceId(),
                payload.status()
        );
    }
}
