package com.myorg.lsf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lsf.config")
public class LsfConfigProperties {

    /**
     * Bootstrap switch for centralized configuration support.
     *
     * <p>Because config import happens before application beans are created, the
     * bootstrap settings should be supplied through environment variables,
     * system properties or command-line arguments.
     */
    private boolean enabled = false;

    private Mode mode = Mode.NONE;

    /**
     * Used for FILE or CONFIGTREE imports.
     */
    private String importLocation = "./config/";

    /**
     * Prefix import with optional: when true.
     */
    private boolean optional = true;

    private ConfigServer configServer = new ConfigServer();

    public enum Mode {
        NONE,
        FILE,
        CONFIGTREE,
        CONFIG_SERVER
    }

    @Data
    public static class ConfigServer {
        private String uri = "http://localhost:8888";
        private boolean failFast = false;
        private String label;
        private String username;
        private String password;
    }
}
