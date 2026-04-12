package com.myorg.lsf.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lsf.discovery")
public class LsfDiscoveryProperties {

    /**
     * Master switch for LSF discovery support.
     */
    private boolean enabled = true;

    /**
     * AUTO: create a local static discovery client when static instances are configured.
     * STATIC: force the local static discovery client.
     * REQUIRED: fail startup unless another DiscoveryClient exists.
     * DISABLED: opt out completely.
     */
    private Mode mode = Mode.AUTO;

    /**
     * Local/static instances used for development, tests or small deployments.
     * Production systems can instead provide another DiscoveryClient implementation
     * (for example Eureka, Consul, Kubernetes) on the classpath.
     */
    private Map<String, List<Instance>> services = new LinkedHashMap<>();

    public enum Mode {
        AUTO,
        STATIC,
        REQUIRED,
        DISABLED
    }

    @Data
    public static class Instance {
        private String host = "localhost";
        private int port;
        private boolean secure = false;
        private String scheme;
        private String contextPath = "";
        private Map<String, String> metadata = new LinkedHashMap<>();
    }

    public List<Instance> instances(String serviceId) {
        return services.getOrDefault(serviceId, new ArrayList<>());
    }
}
