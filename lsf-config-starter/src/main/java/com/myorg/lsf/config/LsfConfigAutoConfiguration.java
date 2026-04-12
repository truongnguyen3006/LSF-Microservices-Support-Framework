package com.myorg.lsf.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties(LsfConfigProperties.class)
public class LsfConfigAutoConfiguration {

    @Bean
    public SmartLifecycle lsfConfigStartupValidator(LsfConfigProperties properties, Environment environment) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                if (!properties.isEnabled() || properties.getMode() == LsfConfigProperties.Mode.NONE) {
                    running = true;
                    return;
                }

                String applicationName = environment.getProperty("spring.application.name");
                if (!StringUtils.hasText(applicationName)) {
                    throw new IllegalStateException(
                            "lsf.config is enabled but spring.application.name is blank. " +
                                    "Set spring.application.name before using centralized configuration."
                    );
                }
                running = true;
            }

            @Override
            public void stop() {
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE;
            }
        };
    }
}
