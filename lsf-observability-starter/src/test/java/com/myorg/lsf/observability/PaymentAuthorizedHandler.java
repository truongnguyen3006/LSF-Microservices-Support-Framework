package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuthorizedHandler {

    private final EventCapture eventCapture;

    public PaymentAuthorizedHandler(EventCapture eventCapture) {
        this.eventCapture = eventCapture;
    }

    @LsfEventHandler(value = "payments.authorized.v1", payload = PaymentAuthorized.class)
    public void onAuthorized(EventEnvelope envelope, PaymentAuthorized payload) {
        LsfRequestContext context = LsfRequestContextHolder.getContext();
        eventCapture.record(new HandledEventSnapshot(
                envelope.getEventType(),
                payload.orderId(),
                envelope.getCorrelationId(),
                envelope.getCausationId(),
                envelope.getRequestId(),
                context == null ? null : context.correlationId(),
                context == null ? null : context.causationId(),
                context == null ? null : context.requestId(),
                MDC.get("corrId"),
                MDC.get("causationId"),
                MDC.get("requestId")
        ));
    }
}
