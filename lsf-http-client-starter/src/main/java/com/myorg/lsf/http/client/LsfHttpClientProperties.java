package com.myorg.lsf.http.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lsf.http.client")
public class LsfHttpClientProperties {

    private boolean enabled = true;

    private Duration connectTimeout = Duration.ofSeconds(1);

    private Duration readTimeout = Duration.ofSeconds(2);

    private Authentication authentication = new Authentication();

    @Data
    public static class Authentication {
        private LsfClientAuthMode mode = LsfClientAuthMode.AUTO;
        private ApiKey apiKey = new ApiKey();
        private Bearer bearer = new Bearer();
    }

    @Data
    public static class ApiKey {
        private String headerName;
        private String value;
    }

    @Data
    public static class Bearer {
        private String token;
    }
}
