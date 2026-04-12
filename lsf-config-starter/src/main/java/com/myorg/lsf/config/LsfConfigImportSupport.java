package com.myorg.lsf.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LsfConfigImportSupport {

    private static final String BOOTSTRAP_SOURCE_NAME = "lsfConfigBootstrap";

    private LsfConfigImportSupport() {
    }

    public static void apply(ConfigurableEnvironment environment) {
        if (!Boolean.parseBoolean(environment.getProperty("lsf.config.enabled", "false"))) {
            return;
        }

        LsfConfigProperties.Mode mode = resolveMode(environment.getProperty("lsf.config.mode", "NONE"));
        if (mode == LsfConfigProperties.Mode.NONE) {
            return;
        }

        Map<String, Object> bootstrap = new LinkedHashMap<>();
        String candidateImport = switch (mode) {
            case FILE -> withOptionalPrefix(
                    "file:" + normalizeLocation(environment.getProperty("lsf.config.import-location", "./config/")),
                    environment
            );
            case CONFIGTREE -> withOptionalPrefix(
                    "configtree:" + normalizeLocation(environment.getProperty("lsf.config.import-location", "./config/")),
                    environment
            );
            case CONFIG_SERVER -> withOptionalPrefix("configserver:", environment);
            case NONE -> null;
        };

        if (candidateImport == null) {
            return;
        }

        appendConfigImport(bootstrap, environment, candidateImport);

        if (mode == LsfConfigProperties.Mode.CONFIG_SERVER) {
            putIfHasText(bootstrap, "spring.cloud.config.uri", environment.getProperty("lsf.config.config-server.uri"));
            bootstrap.put("spring.cloud.config.fail-fast",
                    environment.getProperty("lsf.config.config-server.fail-fast", "false"));
            putIfHasText(bootstrap, "spring.cloud.config.label", environment.getProperty("lsf.config.config-server.label"));
            putIfHasText(bootstrap, "spring.cloud.config.username", environment.getProperty("lsf.config.config-server.username"));
            putIfHasText(bootstrap, "spring.cloud.config.password", environment.getProperty("lsf.config.config-server.password"));
        }

        environment.getPropertySources().remove(BOOTSTRAP_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource(BOOTSTRAP_SOURCE_NAME, bootstrap));
    }

    static LsfConfigProperties.Mode resolveMode(String value) {
        if (!StringUtils.hasText(value)) {
            return LsfConfigProperties.Mode.NONE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        return LsfConfigProperties.Mode.valueOf(normalized);
    }

    private static void appendConfigImport(
            Map<String, Object> bootstrap,
            ConfigurableEnvironment environment,
            String candidateImport
    ) {
        String existing = environment.getProperty("spring.config.import");
        if (!StringUtils.hasText(existing)) {
            bootstrap.put("spring.config.import", candidateImport);
            return;
        }

        boolean alreadyPresent = Arrays.stream(existing.split(","))
                .map(String::trim)
                .anyMatch(candidateImport::equals);
        bootstrap.put("spring.config.import", alreadyPresent ? existing : existing + "," + candidateImport);
    }

    private static String withOptionalPrefix(String location, ConfigurableEnvironment environment) {
        boolean optional = Boolean.parseBoolean(environment.getProperty("lsf.config.optional", "true"));
        return optional ? "optional:" + location : location;
    }

    private static String normalizeLocation(String location) {
        if (!StringUtils.hasText(location)) {
            return "./config/";
        }
        String normalized = location.trim();
        return normalized.endsWith("/") ? normalized : (normalized + "/");
    }

    private static void putIfHasText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }
}
