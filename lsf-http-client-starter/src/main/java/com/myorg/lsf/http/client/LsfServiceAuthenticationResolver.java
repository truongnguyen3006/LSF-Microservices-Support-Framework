package com.myorg.lsf.http.client;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public class LsfServiceAuthenticationResolver {

    private final LsfHttpClientProperties properties;
    private final Environment environment;

    public LsfServiceAuthenticationResolver(LsfHttpClientProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public void apply(HttpHeaders headers, LsfClientAuthMode requestedMode) {
        LsfClientAuthMode mode = resolveMode(requestedMode);
        switch (mode) {
            case NONE -> {
                return;
            }
            case API_KEY -> headers.set(resolveApiKeyHeaderName(), resolveRequiredApiKeyValue());
            case BEARER -> headers.setBearerAuth(resolveRequiredBearerToken());
            case AUTO -> throw new IllegalStateException("AUTO mode should have been resolved before applying headers.");
        }
    }

    public void validate() {
        LsfClientAuthMode mode = resolveMode(properties.getAuthentication().getMode());
        if (mode == LsfClientAuthMode.API_KEY && !StringUtils.hasText(resolveApiKeyValue())) {
            throw new IllegalStateException(
                    "lsf.http.client.authentication.mode=API_KEY but no API key value was configured."
            );
        }
        if (mode == LsfClientAuthMode.BEARER && !StringUtils.hasText(resolveBearerToken())) {
            throw new IllegalStateException(
                    "lsf.http.client.authentication.mode=BEARER but no bearer token was configured."
            );
        }
    }

    private LsfClientAuthMode resolveMode(LsfClientAuthMode requestedMode) {
        LsfClientAuthMode mode = requestedMode == null || requestedMode == LsfClientAuthMode.AUTO
                ? properties.getAuthentication().getMode()
                : requestedMode;
        if (mode != LsfClientAuthMode.AUTO) {
            return mode;
        }
        if (StringUtils.hasText(resolveApiKeyValue())) {
            return LsfClientAuthMode.API_KEY;
        }
        if (StringUtils.hasText(resolveBearerToken())) {
            return LsfClientAuthMode.BEARER;
        }
        return LsfClientAuthMode.NONE;
    }

    private String resolveApiKeyHeaderName() {
        if (StringUtils.hasText(properties.getAuthentication().getApiKey().getHeaderName())) {
            return properties.getAuthentication().getApiKey().getHeaderName();
        }
        return environment.getProperty("lsf.security.api-key.header-name", "X-API-Key");
    }

    private String resolveRequiredApiKeyValue() {
        String value = resolveApiKeyValue();
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("No API key value configured for internal HTTP authentication.");
        }
        return value;
    }

    private String resolveApiKeyValue() {
        if (StringUtils.hasText(properties.getAuthentication().getApiKey().getValue())) {
            return properties.getAuthentication().getApiKey().getValue();
        }
        return environment.getProperty("lsf.security.api-key.value");
    }

    private String resolveRequiredBearerToken() {
        String token = resolveBearerToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("No bearer token configured for internal HTTP authentication.");
        }
        return token;
    }

    private String resolveBearerToken() {
        return properties.getAuthentication().getBearer().getToken();
    }
}
