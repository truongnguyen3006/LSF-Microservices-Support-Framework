package com.myorg.lsf.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LsfConfigAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfConfigAutoConfiguration.class));

    @Test
    void shouldFailFastWhenEnabledWithoutApplicationName() {
        runner.withPropertyValues(
                        "lsf.config.enabled=true",
                        "lsf.config.mode=file"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "lsf.config is enabled but spring.application.name is blank. " +
                                        "Set spring.application.name before using centralized configuration."
                        ));
    }

    @Test
    void shouldStartWhenApplicationNameExists() {
        runner.withPropertyValues(
                        "spring.application.name=test-service",
                        "lsf.config.enabled=true",
                        "lsf.config.mode=configtree"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }
}
