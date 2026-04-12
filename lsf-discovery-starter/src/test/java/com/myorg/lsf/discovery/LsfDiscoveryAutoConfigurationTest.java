package com.myorg.lsf.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;

class LsfDiscoveryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfDiscoveryAutoConfiguration.class));

    @Test
    void shouldCreateStaticDiscoveryClientWhenStaticServicesConfigured() {
        runner.withPropertyValues(
                        "lsf.discovery.enabled=true",
                        "lsf.discovery.mode=auto",
                        "lsf.discovery.services.orders[0].host=localhost",
                        "lsf.discovery.services.orders[0].port=8081",
                        "lsf.discovery.services.orders[0].metadata.zone=local"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DiscoveryClient.class);
                    assertThat(context).hasSingleBean(LsfServiceLocator.class);

                    DiscoveryClient client = context.getBean(DiscoveryClient.class);
                    assertThat(client.getServices()).containsExactly("orders");

                    ServiceInstance instance = client.getInstances("orders").getFirst();
                    assertThat(instance.getUri().toString()).isEqualTo("http://localhost:8081");
                    assertThat(instance.getMetadata()).containsEntry("zone", "local");
                });
    }

    @Test
    void shouldFailFastWhenStaticModeHasNoServices() {
        runner.withPropertyValues(
                        "lsf.discovery.enabled=true",
                        "lsf.discovery.mode=static"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("lsf.discovery.mode=STATIC but no lsf.discovery.services.* entries were configured."));
    }

    @Test
    void shouldFailFastWhenRequiredModeHasNoDiscoveryClient() {
        runner.withPropertyValues(
                        "lsf.discovery.enabled=true",
                        "lsf.discovery.mode=required"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("lsf.discovery.mode=REQUIRED but no DiscoveryClient/ReactiveDiscoveryClient bean was found."));
    }
}
