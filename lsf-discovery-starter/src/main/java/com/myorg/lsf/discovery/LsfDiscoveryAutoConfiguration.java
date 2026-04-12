package com.myorg.lsf.discovery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;

@AutoConfiguration
@ConditionalOnClass(DiscoveryClient.class)
@EnableConfigurationProperties(LsfDiscoveryProperties.class)
@ConditionalOnProperty(prefix = "lsf.discovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfDiscoveryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DiscoveryClient.class)
    @Conditional(StaticDiscoveryCondition.class)
    public DiscoveryClient lsfStaticDiscoveryClient(LsfDiscoveryProperties properties) {
        return new LsfStaticDiscoveryClient(properties);
    }

    @Bean
    @ConditionalOnClass(ReactiveDiscoveryClient.class)
    @ConditionalOnMissingBean(ReactiveDiscoveryClient.class)
    @ConditionalOnBean(DiscoveryClient.class)
    public ReactiveDiscoveryClient lsfStaticReactiveDiscoveryClient(DiscoveryClient discoveryClient) {
        return new LsfStaticReactiveDiscoveryClient(discoveryClient);
    }

    @Bean
    @ConditionalOnBean(DiscoveryClient.class)
    @ConditionalOnMissingBean
    public LsfServiceLocator lsfServiceLocator(DiscoveryClient discoveryClient) {
        return new LsfServiceLocator(discoveryClient);
    }

    @Bean
    public SmartLifecycle lsfDiscoveryLifecycleValidator(
            LsfDiscoveryProperties properties,
            ObjectProvider<DiscoveryClient> discoveryClientProvider,
            ObjectProvider<ReactiveDiscoveryClient> reactiveDiscoveryClientProvider
    ) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                if (properties.getMode() == LsfDiscoveryProperties.Mode.STATIC && properties.getServices().isEmpty()) {
                    throw new IllegalStateException("lsf.discovery.mode=STATIC but no lsf.discovery.services.* entries were configured.");
                }
                if (properties.getMode() == LsfDiscoveryProperties.Mode.REQUIRED
                        && discoveryClientProvider.getIfAvailable() == null
                        && reactiveDiscoveryClientProvider.getIfAvailable() == null) {
                    throw new IllegalStateException(
                            "lsf.discovery.mode=REQUIRED but no DiscoveryClient/ReactiveDiscoveryClient bean was found."
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
