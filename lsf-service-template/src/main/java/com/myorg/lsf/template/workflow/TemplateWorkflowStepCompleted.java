package com.myorg.lsf.template.workflow;

public record TemplateWorkflowStepCompleted(
        String workflowId,
        String stepName,
        String status
) {
}
