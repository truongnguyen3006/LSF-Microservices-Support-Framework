package com.myorg.lsf.template.integration.http;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemplateDependencyGateway {

    private final TemplateDependencyClient dependencyClient;

    public DependencyCapabilitiesResponse fetchCapabilities() {
        return dependencyClient.capabilities();
    }
}
