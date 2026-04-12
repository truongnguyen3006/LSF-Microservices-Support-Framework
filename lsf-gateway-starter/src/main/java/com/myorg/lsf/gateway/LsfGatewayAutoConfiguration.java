package com.myorg.lsf.gateway;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;

@AutoConfiguration
@EnableConfigurationProperties(LsfGatewayProperties.class)
@ConditionalOnClass(RouteLocator.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "lsf.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lsfGatewayRouteLocator")
    public RouteLocator lsfGatewayRouteLocator(RouteLocatorBuilder builder, LsfGatewayProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();

        for (LsfGatewayProperties.Route route : properties.getRoutes()) {
            validate(route);
            routes.route(route.getId(), predicate -> {
                var routeSpec = predicate.path(route.getPath());

                if (!route.getMethods().isEmpty()) {
                    HttpMethod[] methods = route.getMethods().stream()
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .map(HttpMethod::valueOf)
                            .toArray(HttpMethod[]::new);
                    routeSpec = routeSpec.and().method(methods);
                }

                if (hasFilters(route)) {
                    return routeSpec.filters(filter -> {
                        if (route.getStripPrefix() != null && route.getStripPrefix() > 0) {
                            filter.stripPrefix(route.getStripPrefix());
                        }
                        route.getAddRequestHeaders().forEach(filter::addRequestHeader);
                        route.getAddResponseHeaders().forEach(filter::addResponseHeader);
                        return filter;
                    }).uri(route.getUri());
                }

                return routeSpec.uri(route.getUri());
            });
        }

        return routes.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "lsfCorrelationIdGlobalFilter")
    public GlobalFilter lsfCorrelationIdGlobalFilter(LsfGatewayProperties properties) {
        return new LsfCorrelationIdGlobalFilter(properties);
    }

    private static boolean hasFilters(LsfGatewayProperties.Route route) {
        return (route.getStripPrefix() != null && route.getStripPrefix() > 0)
                || !route.getAddRequestHeaders().isEmpty()
                || !route.getAddResponseHeaders().isEmpty();
    }

    private static void validate(LsfGatewayProperties.Route route) {
        if (route.getId() == null || route.getId().isBlank()) {
            throw new IllegalStateException("lsf.gateway.routes[*].id must not be blank");
        }
        if (route.getPath() == null || route.getPath().isBlank()) {
            throw new IllegalStateException("lsf.gateway.routes[" + route.getId() + "].path must not be blank");
        }
        if (route.getUri() == null || route.getUri().isBlank()) {
            throw new IllegalStateException("lsf.gateway.routes[" + route.getId() + "].uri must not be blank");
        }
    }
}
