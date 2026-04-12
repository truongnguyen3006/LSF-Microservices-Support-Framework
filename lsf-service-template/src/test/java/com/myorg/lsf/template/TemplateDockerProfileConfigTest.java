package com.myorg.lsf.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateDockerProfileConfigTest {

    @Test
    void dockerProfileUsesContainerFriendlyDiscoveryAndKafkaDefaults() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run("--spring.profiles.active=docker")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("lsf.discovery.services.dependency-service[0].uri"))
                    .isEqualTo("http://lsf-example:8080");
            assertThat(environment.getProperty("lsf.kafka.bootstrap-servers")).isEqualTo("kafka:29092");
            assertThat(environment.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .contains("prometheus");
        }
    }

    @SpringBootConfiguration
    static class TestApplication {
    }
}
