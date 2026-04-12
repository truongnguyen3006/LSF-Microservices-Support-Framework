package com.myorg.lsf.outbox.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LsfOutboxAdminAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfOutboxAdminAutoConfiguration.class))
            .withUserConfiguration(JdbcConfig.class);

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfOutboxAdminAutoConfiguration.class))
            .withUserConfiguration(JdbcConfig.class);

    @Test
    void shouldStayDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OutboxAdminService.class);
            assertThat(context).doesNotHaveBean(OutboxAdminController.class);
        });
    }

    @Test
    void shouldCreateRepositoryAndServiceWithoutControllerInNonWebContext() {
        runner.withPropertyValues("lsf.outbox.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcOutboxAdminRepository.class);
                    assertThat(context).hasSingleBean(OutboxAdminService.class);
                    assertThat(context).doesNotHaveBean(OutboxAdminController.class);
                });
    }

    @Test
    void shouldCreateControllerInServletWebContextWhenEnabled() {
        webRunner.withPropertyValues("lsf.outbox.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcOutboxAdminRepository.class);
                    assertThat(context).hasSingleBean(OutboxAdminService.class);
                    assertThat(context).hasSingleBean(OutboxAdminController.class);
                });
    }

    @Configuration
    static class JdbcConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:auto-config-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
