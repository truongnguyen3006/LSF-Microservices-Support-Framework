package it;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleDockerProfileConfigTest {

    @Test
    void dockerProfileUsesContainerFriendlyInfrastructureDefaults() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run("--spring.profiles.active=docker")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("lsf.kafka.bootstrap-servers")).isEqualTo("kafka:29092");
            assertThat(environment.getProperty("lsf.kafka.schema-registry-url")).isEqualTo("http://schema-registry:8081");
            assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
            assertThat(environment.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
        }
    }

    @SpringBootConfiguration
    static class TestApplication {
    }
}
