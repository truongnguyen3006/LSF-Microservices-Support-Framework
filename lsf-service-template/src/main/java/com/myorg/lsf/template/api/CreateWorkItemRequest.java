package com.myorg.lsf.template.api;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkItemRequest(
        @NotBlank String workItemId,
        @NotBlank String operation
) {
}
