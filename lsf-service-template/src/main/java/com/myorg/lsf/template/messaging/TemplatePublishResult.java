package com.myorg.lsf.template.messaging;

public record TemplatePublishResult(
        String publicationMode,
        String correlationId,
        String requestId
) {
}
