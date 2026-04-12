package com.myorg.lsf.template.integration.http;

import com.myorg.lsf.http.client.LsfClientAuthMode;
import com.myorg.lsf.http.client.LsfHttpClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@LsfHttpClient(
        serviceId = "dependency-service",
        pathPrefix = "/internal",
        resilienceId = "dependency-service-http",
        authMode = LsfClientAuthMode.API_KEY
)
@HttpExchange
public interface TemplateDependencyClient {

    @GetExchange("/template/capabilities")
    DependencyCapabilitiesResponse capabilities();
}
