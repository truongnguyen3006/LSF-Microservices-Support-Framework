package com.myorg.lsf.observability;

import com.myorg.lsf.eventing.LsfDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration(after = {
        MetricsAutoConfiguration.class,
        CompositeMeterRegistryAutoConfiguration.class,
        PrometheusMetricsExportAutoConfiguration.class
})
@ConditionalOnClass(LsfDispatcher.class)
@EnableConfigurationProperties(LsfObservabilityProperties.class)
public class LsfObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    public LsfMetrics lsfMetrics(MeterRegistry registry, Environment env, LsfObservabilityProperties props) {
        String app = env.getProperty("spring.application.name", "unknown-service");
        return new LsfMetrics(registry, app, props);
    }

    /**
     * Pre-register meters at startup so /actuator/metrics/<name> never returns 404.
     */
    @Bean
    public SmartLifecycle lsfMetricsPreRegisterLifecycle(
            LsfObservabilityProperties props,
            ObjectProvider<LsfMetrics> metricsProvider
    ) {
        return new SmartLifecycle() {
            private boolean running = false;

            @Override public void start() {
                if (!props.isEnabled() || !props.isMetricsEnabled()) {
                    running = true;
                    return;
                }
                LsfMetrics m = metricsProvider.getIfAvailable();
                if (m != null) m.preRegisterBaseMeters();
                running = true;
            }

            @Override public void stop() { running = false; }
            @Override public boolean isRunning() { return running; }
            @Override public int getPhase() { return Integer.MIN_VALUE; } // start very early
        };
    }

    @Bean
    public static BeanPostProcessor observingDispatcherBpp(
            LsfObservabilityProperties props,
            ObjectProvider<LsfMetrics> metricsProvider,
            ObjectProvider<ObservationRegistry> observationRegistryProvider
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!props.isEnabled()) return bean;
                if (!(bean instanceof LsfDispatcher)) return bean;
                if (bean instanceof ObservingLsfDispatcher) return bean;

                LsfMetrics metrics = metricsProvider.getIfAvailable();
                ObservationRegistry registry = observationRegistryProvider.getIfAvailable();
                return new ObservingLsfDispatcher((LsfDispatcher) bean, props, metrics, registry);
            }
        };
    }
}
