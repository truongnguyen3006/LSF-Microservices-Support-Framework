package com.myorg.lsf.template.workflow;

public record TemplateWorkflowStepRequested(
        String workflowId,
        String stepName
) {
}
