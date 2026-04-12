package com.myorg.lsf.resilience;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AutoConfiguration
@EnableConfigurationProperties(LsfResilienceProperties.class)
@ConditionalOnProperty(prefix = "lsf.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LsfResiliencePolicyResolver lsfResiliencePolicyResolver(LsfResilienceProperties properties) {
        return new LsfResiliencePolicyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfResilienceComponents lsfResilienceComponents(LsfResiliencePolicyResolver resolver) {
        return new LsfResilienceComponents(resolver);
    }

    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "lsfResilienceExecutorService")
    public ExecutorService lsfResilienceExecutorService() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "lsf-resilience");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfResilienceExecutor lsfResilienceExecutor(
            LsfResilienceComponents components,
            ExecutorService lsfResilienceExecutorService
    ) {
        return new LsfResilienceExecutor(components, lsfResilienceExecutorService);
    }
}
