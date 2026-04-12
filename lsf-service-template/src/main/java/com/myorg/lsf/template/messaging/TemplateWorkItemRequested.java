package com.myorg.lsf.template.messaging;

public record TemplateWorkItemRequested(
        String workItemId,
        String operation
) {
}
