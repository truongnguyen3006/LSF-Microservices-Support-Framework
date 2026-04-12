package com.myorg.lsf.saga;

import com.myorg.lsf.kafka.KafkaAutoConfiguration;
import com.myorg.lsf.kafka.KafkaProducerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class LsfSagaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfSagaAutoConfiguration.class));

    @Test
    void shouldCreateInMemorySagaRepositoryByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LsfSagaOrchestrator.class);
            assertThat(context).hasSingleBean(SagaInstanceRepository.class);
            assertThat(context.getBean(SagaInstanceRepository.class)).isInstanceOf(InMemorySagaInstanceRepository.class);
        });
    }

    @Test
    void shouldFailFastWhenDirectTransportHasNoKafkaTemplate() {
        runner.withPropertyValues("lsf.saga.transport.mode=direct")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("lsf.saga.transport.mode=direct but no KafkaTemplate bean was found."));
    }

    @Test
    void shouldAllowDirectTransportWhenKafkaStarterProvidesKafkaTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class,
                        KafkaProducerAutoConfiguration.class,
                        LsfSagaAutoConfiguration.class
                ))
                .withPropertyValues(
                        "lsf.saga.transport.mode=direct",
                        "lsf.kafka.bootstrap-servers=unused:9092",
                        "lsf.kafka.schema-registry-url=mock://lsf-saga-auto-config"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LsfSagaOrchestrator.class);
                    assertThat(context).hasBean("kafkaTemplate");
                });
    }

    @Test
    void shouldCreateJdbcSagaRepositoryWhenJdbcStoreIsConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JdbcTemplateAutoConfiguration.class,
                        LsfSagaAutoConfiguration.class
                ))
                .withUserConfiguration(TestJdbcConfig.class)
                .withPropertyValues(
                        "lsf.saga.store=jdbc"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SagaInstanceRepository.class);
                    assertThat(context.getBean(SagaInstanceRepository.class)).isInstanceOf(JdbcSagaInstanceRepository.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestJdbcConfig {
        @Bean(destroyMethod = "shutdown")
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }
    }
}
