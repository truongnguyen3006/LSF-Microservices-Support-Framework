package com.myorg.lsf.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LsfGatewayAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfGatewayAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldCreateRouteLocatorFromLsfGatewayProperties() {
        runner.withPropertyValues(
                        "lsf.gateway.routes[0].id=demo-route",
                        "lsf.gateway.routes[0].path=/api/orders/**",
                        "lsf.gateway.routes[0].uri=http://localhost:8081",
                        "lsf.gateway.routes[0].strip-prefix=1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RouteLocator.class);

                    RouteLocator routeLocator = context.getBean(RouteLocator.class);
                    List<String> ids = routeLocator.getRoutes()
                            .map(route -> route.getId())
                            .collectList()
                            .block(Duration.ofSeconds(5));
                    assertThat(ids).contains("demo-route");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        WebFluxProperties webFluxProperties() {
            return new WebFluxProperties();
        }

        @Bean
        RouteLocatorBuilder routeLocatorBuilder(org.springframework.context.ConfigurableApplicationContext context) {
            return new RouteLocatorBuilder(context);
        }

        @Bean
        PathRoutePredicateFactory pathRoutePredicateFactory(WebFluxProperties webFluxProperties) {
            return new PathRoutePredicateFactory(webFluxProperties);
        }

        @Bean
        MethodRoutePredicateFactory methodRoutePredicateFactory() {
            return new MethodRoutePredicateFactory();
        }

        @Bean
        StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
            return new StripPrefixGatewayFilterFactory();
        }

        @Bean
        AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
            return new AddRequestHeaderGatewayFilterFactory();
        }

        @Bean
        AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
            return new AddResponseHeaderGatewayFilterFactory();
        }
    }
}
