package com.myorg.lsf.template.application;

import com.myorg.lsf.template.api.CreateWorkItemRequest;
import com.myorg.lsf.template.api.CreateWorkItemResponse;
import com.myorg.lsf.template.integration.http.DependencyCapabilitiesResponse;
import com.myorg.lsf.template.integration.http.TemplateDependencyGateway;
import com.myorg.lsf.template.messaging.TemplateIntegrationEventPublisher;
import com.myorg.lsf.template.messaging.TemplatePublishResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemplateWorkItemService {

    private final TemplateDependencyGateway dependencyGateway;
    private final TemplateIntegrationEventPublisher integrationEventPublisher;

    public CreateWorkItemResponse submitWorkItem(CreateWorkItemRequest request) {
        DependencyCapabilitiesResponse capabilities = dependencyGateway.fetchCapabilities();
        TemplatePublishResult publishResult = integrationEventPublisher.publishRequestedEvent(request);
        return new CreateWorkItemResponse(
                request.workItemId(),
                capabilities.status(),
                publishResult.publicationMode(),
                publishResult.correlationId(),
                publishResult.requestId()
        );
    }
}
