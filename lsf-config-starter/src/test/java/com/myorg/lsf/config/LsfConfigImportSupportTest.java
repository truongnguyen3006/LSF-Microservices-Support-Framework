package com.myorg.lsf.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LsfConfigImportSupportTest {

    @Test
    void shouldAppendOptionalFileImport() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "lsf.config.enabled", "true",
                "lsf.config.mode", "file",
                "lsf.config.import-location", "./ops-config"
        )));

        LsfConfigImportSupport.apply(environment);

        assertThat(environment.getProperty("spring.config.import"))
                .isEqualTo("optional:file:./ops-config/");
    }

    @Test
    void shouldAppendConfigServerImportWithoutDuplicatingExistingEntries() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "lsf.config.enabled", "true",
                "lsf.config.mode", "config-server",
                "lsf.config.config-server.uri", "http://localhost:9999",
                "spring.config.import", "optional:file:./base/"
        )));

        LsfConfigImportSupport.apply(environment);

        assertThat(environment.getProperty("spring.config.import"))
                .isEqualTo("optional:file:./base/,optional:configserver:");
        assertThat(environment.getProperty("spring.cloud.config.uri"))
                .isEqualTo("http://localhost:9999");
    }

    @Test
    void shouldDoNothingWhenDisabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "lsf.config.enabled", "false",
                "lsf.config.mode", "file"
        )));

        LsfConfigImportSupport.apply(environment);

        assertThat(environment.getProperty("spring.config.import")).isNull();
    }
}
