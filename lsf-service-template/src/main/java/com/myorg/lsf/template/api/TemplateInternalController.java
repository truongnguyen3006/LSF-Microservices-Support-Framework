package com.myorg.lsf.template.api;

import com.myorg.lsf.template.application.TemplateWorkItemService;
import com.myorg.lsf.template.integration.http.DependencyCapabilitiesResponse;
import com.myorg.lsf.template.integration.http.TemplateDependencyGateway;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/template")
@RequiredArgsConstructor
public class TemplateInternalController {

    private final TemplateWorkItemService workItemService;
    private final TemplateDependencyGateway dependencyGateway;

    @GetMapping("/context")
    public CurrentRequestContextResponse context() {
        return CurrentRequestContextResponse.fromCurrentContext();
    }

    @GetMapping("/dependency/capabilities")
    public DependencyCapabilitiesResponse dependencyCapabilities() {
        return dependencyGateway.fetchCapabilities();
    }

    @PostMapping("/work-items")
    public ResponseEntity<CreateWorkItemResponse> createWorkItem(@Valid @RequestBody CreateWorkItemRequest request) {
        return ResponseEntity.accepted().body(workItemService.submitWorkItem(request));
    }
}
