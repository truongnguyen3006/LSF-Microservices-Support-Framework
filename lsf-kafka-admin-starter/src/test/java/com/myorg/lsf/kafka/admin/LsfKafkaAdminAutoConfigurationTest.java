package com.myorg.lsf.kafka.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.kafka.KafkaProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LsfKafkaAdminAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfKafkaAdminAutoConfiguration.class))
            .withUserConfiguration(BaseConfig.class);

    @Test
    void shouldBeDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(LsfKafkaDlqService.class);
            assertThat(context).doesNotHaveBean(LsfKafkaDlqController.class);
        });
    }

    @Test
    void shouldCreateAdminBeansAndPreRegisterReplayMetricsWhenEnabled() {
        runner.withPropertyValues(
                        "lsf.kafka.admin.enabled=true",
                        "lsf.kafka.bootstrap-servers=localhost:9092",
                        "spring.application.name=ops-api"
                )
                .withUserConfiguration(MetricsConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LsfKafkaDlqService.class);
                    assertThat(context).hasSingleBean(LsfKafkaDlqController.class);
                    assertThat(context).hasSingleBean(LsfKafkaReplayMetrics.class);
                    assertThat(context.getBean(SimpleMeterRegistry.class)
                            .find("lsf.kafka.replay.success")
                            .tag("service", "ops-api")
                            .counter()).isNotNull();
                });
    }

    @Test
    void shouldFailFastWhenBootstrapServersAreMissing() {
        runner.withPropertyValues("lsf.kafka.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("lsf.kafka.bootstrap-servers");
                });
    }

    @Configuration
    static class BaseConfig {

        @Bean
        KafkaProperties kafkaProperties(org.springframework.core.env.Environment environment) {
            KafkaProperties properties = new KafkaProperties();
            properties.setBootstrapServers(environment.getProperty("lsf.kafka.bootstrap-servers"));
            return properties;
        }

        @Bean
        ConsumerFactory<String, Object> consumerFactory() {
            return mock(ConsumerFactory.class);
        }

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration
    static class MetricsConfig {

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
