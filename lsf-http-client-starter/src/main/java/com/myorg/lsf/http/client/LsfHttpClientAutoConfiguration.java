package com.myorg.lsf.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.discovery.LsfServiceLocator;
import com.myorg.lsf.resilience.LsfResilienceExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnBean(LsfServiceLocator.class)
@EnableConfigurationProperties(LsfHttpClientProperties.class)
@ConditionalOnProperty(prefix = "lsf.http.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder lsfRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfServiceAuthenticationResolver lsfServiceAuthenticationResolver(
            LsfHttpClientProperties properties,
            Environment environment
    ) {
        return new LsfServiceAuthenticationResolver(properties, environment);
    }

    @Bean
    public SmartLifecycle lsfHttpClientStartupValidator(LsfServiceAuthenticationResolver authenticationResolver) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                authenticationResolver.validate();
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

    @Bean
    @ConditionalOnMissingBean
    public LsfHttpServiceClientFactory lsfHttpServiceClientFactory(
            RestClient.Builder restClientBuilder,
            LsfServiceLocator serviceLocator,
            ObjectProvider<LsfResilienceExecutor> resilienceExecutorProvider,
            LsfHttpClientProperties properties,
            LsfServiceAuthenticationResolver authenticationResolver,
            ObjectMapper objectMapper
    ) {
        return new LsfHttpServiceClientFactory(
                restClientBuilder,
                serviceLocator,
                resilienceExecutorProvider,
                properties,
                authenticationResolver,
                objectMapper
        );
    }
}
