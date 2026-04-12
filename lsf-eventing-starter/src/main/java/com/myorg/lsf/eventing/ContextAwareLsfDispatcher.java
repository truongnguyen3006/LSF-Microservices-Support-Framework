package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

import java.util.Map;

public class ContextAwareLsfDispatcher implements LsfDispatcher {

    private final LsfDispatcher delegate;

    public ContextAwareLsfDispatcher(LsfDispatcher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void dispatch(EventEnvelope env) {
        LsfRequestContext previousRequestContext = LsfRequestContextHolder.getContext();
        LsfTraceContext previousTraceContext = LsfTraceContextHolder.getContext();

        try {
            installRequestContext(env);
            installTraceContext(env);
            delegate.dispatch(env);
        } finally {
            LsfRequestContextHolder.setContext(previousRequestContext);
            LsfTraceContextHolder.setContext(previousTraceContext);
        }
    }

    private static void installRequestContext(EventEnvelope env) {
        if (env == null) {
            LsfRequestContextHolder.clear();
            return;
        }
        LsfRequestContextHolder.setContext(new LsfRequestContext(
                env.getCorrelationId(),
                env.getCausationId(),
                env.getRequestId()
        ));
    }

    private static void installTraceContext(EventEnvelope env) {
        if (env == null || env.getTraceHeaders() == null || env.getTraceHeaders().isEmpty()) {
            LsfTraceContextHolder.clear();
            return;
        }
        LsfTraceContextHolder.setContext(new LsfTraceContext(Map.copyOf(env.getTraceHeaders())));
    }
}
