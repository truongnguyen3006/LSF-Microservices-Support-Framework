package com.myorg.lsf.template.config;

import com.myorg.lsf.template.messaging.TemplateEventTypes;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "template.service")
public class TemplateServiceProperties {

    @NotBlank
    private String integrationTopic = "template.integration.events";

    @NotBlank
    private String integrationEventType = TemplateEventTypes.RESOURCE_REQUESTED_V1;

    @NotBlank
    private String workflowReplyTopic = "template.workflow.replies";

    @NotBlank
    private String workflowCompletedEventType = TemplateEventTypes.WORKFLOW_STEP_COMPLETED_V1;
}
