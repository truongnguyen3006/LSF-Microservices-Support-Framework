package com.myorg.lsf.http.client;

import com.myorg.lsf.discovery.LsfServiceLocator;
import org.springframework.util.StringUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

public class LsfServiceUriBuilderFactory implements UriBuilderFactory {

    private final LsfServiceLocator serviceLocator;
    private final String serviceId;
    private final String pathPrefix;

    public LsfServiceUriBuilderFactory(LsfServiceLocator serviceLocator, String serviceId, String pathPrefix) {
        this.serviceLocator = serviceLocator;
        this.serviceId = serviceId;
        this.pathPrefix = pathPrefix;
    }

    @Override
    public UriBuilder uriString(String uriTemplate) {
        return delegate().uriString(uriTemplate);
    }

    @Override
    public UriBuilder builder() {
        return delegate().builder();
    }

    @Override
    public URI expand(String uriTemplate, Map<String, ?> uriVariables) {
        return delegate().expand(uriTemplate, uriVariables);
    }

    @Override
    public URI expand(String uriTemplate, Object... uriVariables) {
        return delegate().expand(uriTemplate, uriVariables);
    }

    private DefaultUriBuilderFactory delegate() {
        URI baseUri = serviceLocator.getRequiredUri(serviceId);
        String baseUrl = baseUri.toString();
        if (StringUtils.hasText(pathPrefix)) {
            baseUrl = UriComponentsBuilder.fromUri(baseUri)
                    .path(pathPrefix.startsWith("/") ? pathPrefix : "/" + pathPrefix)
                    .build()
                    .toUriString();
        }
        return new DefaultUriBuilderFactory(baseUrl);
    }
}
