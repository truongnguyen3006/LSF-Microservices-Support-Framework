package com.myorg.lsf.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lsf.security")
public class LsfSecurityProperties {

    private boolean enabled = false;

    private Mode mode = Mode.API_KEY;

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/info"
    ));

    private List<String> adminPaths = new ArrayList<>(List.of(
            "/lsf/outbox/**"
    ));

    private List<String> adminAuthorities = new ArrayList<>(List.of("ROLE_LSF_ADMIN"));

    private ApiKey apiKey = new ApiKey();

    private Jwt jwt = new Jwt();

    public enum Mode {
        API_KEY,
        JWT
    }

    @Data
    public static class ApiKey {
        private String headerName = "X-API-Key";
        private String value;
        private String principal = "lsf-internal";
        private List<String> authorities = new ArrayList<>(List.of("ROLE_LSF_INTERNAL"));
    }

    @Data
    public static class Jwt {
        private String issuerUri;
        private String jwkSetUri;
        private String hmacSecret;
        private String authoritiesClaim = "scope";
        private String authorityPrefix = "SCOPE_";
    }
}
