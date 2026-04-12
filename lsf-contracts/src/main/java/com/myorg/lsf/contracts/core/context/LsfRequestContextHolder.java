package com.myorg.lsf.contracts.core.context;

public final class LsfRequestContextHolder {

    private static final ThreadLocal<LsfRequestContext> CONTEXT = new ThreadLocal<>();

    private LsfRequestContextHolder() {
    }

    public static LsfRequestContext getContext() {
        return CONTEXT.get();
    }

    public static void setContext(LsfRequestContext context) {
        if (context == null) {
            CONTEXT.remove();
            return;
        }
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
