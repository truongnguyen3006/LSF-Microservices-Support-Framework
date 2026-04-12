package com.myorg.lsf.template.api;

public record CreateWorkItemResponse(
        String workItemId,
        String dependencyStatus,
        String publicationMode,
        String correlationId,
        String requestId
) {
}
