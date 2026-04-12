package com.myorg.lsf.template.api;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;

public record CurrentRequestContextResponse(
        String correlationId,
        String causationId,
        String requestId
) {
    public static CurrentRequestContextResponse fromCurrentContext() {
        LsfRequestContext context = LsfRequestContextHolder.getContext();
        if (context == null) {
            return new CurrentRequestContextResponse(null, null, null);
        }
        return new CurrentRequestContextResponse(
                context.correlationId(),
                context.causationId(),
                context.requestId()
        );
    }
}
