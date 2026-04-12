package com.myorg.lsf.http.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class LsfAuthenticationInterceptor implements ClientHttpRequestInterceptor {

    private final LsfServiceAuthenticationResolver authenticationResolver;
    private final LsfClientAuthMode authMode;

    public LsfAuthenticationInterceptor(
            LsfServiceAuthenticationResolver authenticationResolver,
            LsfClientAuthMode authMode
    ) {
        this.authenticationResolver = authenticationResolver;
        this.authMode = authMode;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        authenticationResolver.apply(request.getHeaders(), authMode);
        return execution.execute(request, body);
    }
}
