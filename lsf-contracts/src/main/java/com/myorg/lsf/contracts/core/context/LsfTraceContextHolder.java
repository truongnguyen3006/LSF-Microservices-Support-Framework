package com.myorg.lsf.contracts.core.context;

public final class LsfTraceContextHolder {

    private static final ThreadLocal<LsfTraceContext> CONTEXT = new ThreadLocal<>();

    private LsfTraceContextHolder() {
    }

    public static LsfTraceContext getContext() {
        return CONTEXT.get();
    }

    public static void setContext(LsfTraceContext context) {
        if (context == null || context.isEmpty()) {
            CONTEXT.remove();
            return;
        }
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
