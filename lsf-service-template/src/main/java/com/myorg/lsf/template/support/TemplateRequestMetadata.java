package com.myorg.lsf.template.support;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import org.springframework.util.StringUtils;

public record TemplateRequestMetadata(
        String correlationId,
        String causationId,
        String requestId
) {
    public static TemplateRequestMetadata capture(String fallbackCorrelationId) {
        LsfRequestContext context = LsfRequestContextHolder.getContext();
        if (context == null) {
            return new TemplateRequestMetadata(fallbackCorrelationId, null, null);
        }

        String correlationId = StringUtils.hasText(context.correlationId())
                ? context.correlationId()
                : fallbackCorrelationId;

        return new TemplateRequestMetadata(
                correlationId,
                context.causationId(),
                context.requestId()
        );
    }
}
