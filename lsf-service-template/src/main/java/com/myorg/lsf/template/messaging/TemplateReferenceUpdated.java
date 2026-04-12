package com.myorg.lsf.template.messaging;

public record TemplateReferenceUpdated(
        String referenceId,
        String status
) {
}
