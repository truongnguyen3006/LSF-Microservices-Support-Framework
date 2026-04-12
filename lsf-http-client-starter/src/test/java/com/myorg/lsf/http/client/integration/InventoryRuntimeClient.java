package com.myorg.lsf.http.client.integration;

import com.myorg.lsf.http.client.LsfClientAuthMode;
import com.myorg.lsf.http.client.LsfHttpClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@LsfHttpClient(
        serviceId = "inventory-service",
        pathPrefix = "/internal/runtime",
        resilienceId = "inventory-http",
        authMode = LsfClientAuthMode.API_KEY
)
@HttpExchange
public interface InventoryRuntimeClient {

    @GetExchange("/success")
    RuntimeResponse success();

    @GetExchange("/retryable")
    RuntimeResponse retryable();

    @GetExchange("/non-retryable")
    RuntimeResponse nonRetryable();
}
