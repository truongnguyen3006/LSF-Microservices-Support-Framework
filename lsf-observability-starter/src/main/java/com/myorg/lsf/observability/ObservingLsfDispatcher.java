package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

@RequiredArgsConstructor
public class ObservingLsfDispatcher implements LsfDispatcher {

    private final LsfDispatcher delegate;
    private final LsfObservabilityProperties props;
    private final LsfMetrics metrics; // can be null if metrics disabled
    private final ObservationRegistry observationRegistry; // nullable

    @Override
    public void dispatch(EventEnvelope env) {
        if (props.isMdcEnabled()) {
            putMdc(env);
        }

        Observation obs = null;
        Observation.Scope scope = null;

        if (props.isTracingEnabled() && observationRegistry != null) {
            obs = Observation.start("lsf.event.dispatch", observationRegistry);

            // low-cardinality tags
            if (props.isTagEventType() && env != null && env.getEventType() != null) {
                obs.lowCardinalityKeyValue("event.type", env.getEventType());
            }
            scope = obs.openScope();
        }

        Timer.Sample sample = (metrics != null && props.isMetricsEnabled())
                ? metrics.startTimer()
                : null;

        try {
            delegate.dispatch(env);

            String outcome = LsfDispatchOutcome.consume();
            if (outcome == null) outcome = "success";

            if (obs != null) {
                if (props.isTagOutcome()) obs.lowCardinalityKeyValue("outcome", outcome);
            }

            if (metrics != null && props.isMetricsEnabled()) {
                switch (outcome) {
                    case LsfDispatchOutcome.DUPLICATE -> metrics.incDuplicate();
                    case LsfDispatchOutcome.IN_FLIGHT -> metrics.incInFlight();
                    default -> metrics.incHandledSuccess();
                }
                metrics.stopTimer(sample, env, outcome);
            }

            if (obs != null) obs.stop();
        } catch (RuntimeException e) {
            if (obs != null) {
                obs.error(e);
                obs.lowCardinalityKeyValue("outcome", "fail");
                obs.stop();
            }
            if (metrics != null && props.isMetricsEnabled()) {
                metrics.incHandledFail();
                metrics.stopTimer(sample, env, "fail");
            }
            throw e;
        } finally {
            LsfDispatchOutcome.clear();
            if (scope != null) scope.close();
            if (props.isMdcEnabled()) clearMdc();
        }
    }


    private void putMdc(EventEnvelope env) {
        if (env == null) return;
        if (env.getCorrelationId() != null) MDC.put("corrId", env.getCorrelationId());
        if (env.getEventId() != null) MDC.put("eventId", env.getEventId());
        if (env.getEventType() != null) MDC.put("eventType", env.getEventType());
        if (env.getCausationId() != null) MDC.put("causationId", env.getCausationId());
        if (env.getRequestId() != null) MDC.put("requestId", env.getRequestId());
        if (env.getTraceHeaders() != null && !env.getTraceHeaders().isEmpty()) {
            putTraceIds(env);
        }
    }

    private void clearMdc() {
        MDC.remove("corrId");
        MDC.remove("eventId");
        MDC.remove("eventType");
        MDC.remove("causationId");
        MDC.remove("requestId");
        MDC.remove("traceId");
        MDC.remove("spanId");
    }

    private void putTraceIds(EventEnvelope env) {
        if (MDC.get("traceId") != null && MDC.get("spanId") != null) {
            return;
        }

        String traceparent = env.getTraceHeaders().get(CoreHeaders.TRACEPARENT);
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                MDC.put("traceId", parts[1]);
                MDC.put("spanId", parts[2]);
                return;
            }
        }

        String traceId = env.getTraceHeaders().get(CoreHeaders.X_B3_TRACE_ID);
        String spanId = env.getTraceHeaders().get(CoreHeaders.X_B3_SPAN_ID);
        if (traceId != null && spanId != null) {
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);
        }
    }
}
