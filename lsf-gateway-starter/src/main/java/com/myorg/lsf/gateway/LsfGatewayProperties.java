package com.myorg.lsf.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lsf.gateway")
public class LsfGatewayProperties {

    private boolean enabled = true;

    private String correlationHeader = "X-Correlation-Id";

    private boolean generateCorrelationId = true;

    private boolean echoCorrelationIdResponse = true;

    private boolean generateRequestId = true;

    private boolean echoRequestIdResponse = true;

    private List<Route> routes = new ArrayList<>();

    @Data
    public static class Route {
        private String id;
        private String path;
        private String uri;
        private List<String> methods = new ArrayList<>();
        private Integer stripPrefix;
        private Map<String, String> addRequestHeaders = new LinkedHashMap<>();
        private Map<String, String> addResponseHeaders = new LinkedHashMap<>();
    }
}
