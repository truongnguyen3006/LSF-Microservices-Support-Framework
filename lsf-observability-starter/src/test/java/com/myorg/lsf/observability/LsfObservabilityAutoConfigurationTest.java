package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LsfObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfObservabilityAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("spring.application.name=inventory-service");

    @Test
    void shouldWrapDispatcherAndPreRegisterBaseMetersWhenEnabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LsfMetrics.class);
            assertThat(context.getBean(LsfDispatcher.class)).isInstanceOf(ObservingLsfDispatcher.class);

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find("lsf.event.handled.success")
                    .tag("service", "inventory-service")
                    .counter()).isNotNull();
            assertThat(registry.find("lsf.event.processing")
                    .tag("service", "inventory-service")
                    .timer()).isNotNull();
        });
    }

    @Test
    void shouldLeaveDispatcherUntouchedWhenObservabilityDisabled() {
        runner.withPropertyValues("lsf.observability.enabled=false")
                .run(context -> assertThat(context.getBean(LsfDispatcher.class)).isInstanceOf(TestDispatcher.class));
    }

    @Configuration
    static class TestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }

        @Bean
        LsfDispatcher testDispatcher() {
            return new TestDispatcher();
        }
    }

    static class TestDispatcher implements LsfDispatcher {
        @Override
        public void dispatch(EventEnvelope env) {
        }
    }
}
