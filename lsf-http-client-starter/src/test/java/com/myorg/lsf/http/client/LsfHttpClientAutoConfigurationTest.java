package com.myorg.lsf.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import static org.assertj.core.api.Assertions.assertThat;

class LsfHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    com.myorg.lsf.discovery.LsfDiscoveryAutoConfiguration.class,
                    LsfHttpClientAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "lsf.discovery.enabled=true",
                    "lsf.discovery.mode=static",
                    "lsf.discovery.services.inventory-service[0].host=inventory.local",
                    "lsf.discovery.services.inventory-service[0].port=80"
            );

    @Test
    void shouldRegisterAnnotatedHttpClient() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AutoConfigInventoryClient.class);
            assertThat(context).hasSingleBean(LsfHttpServiceClientFactory.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableLsfHttpClients(basePackageClasses = AutoConfigInventoryClient.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @LsfHttpClient(serviceId = "inventory-service")
    @HttpExchange
    interface AutoConfigInventoryClient {
        @GetExchange("/health")
        String health();
    }
}
