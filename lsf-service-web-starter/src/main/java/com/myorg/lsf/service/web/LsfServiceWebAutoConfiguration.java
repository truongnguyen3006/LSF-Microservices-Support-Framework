package com.myorg.lsf.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(LsfServiceWebProperties.class)
@ConditionalOnProperty(prefix = "lsf.service.web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfServiceWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LsfErrorResponseFactory lsfErrorResponseFactory(Environment environment) {
        return new LsfErrorResponseFactory(environment.getProperty("spring.application.name"));
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfErrorResponseWriter lsfErrorResponseWriter(
            ObjectMapper objectMapper,
            LsfErrorResponseFactory errorResponseFactory
    ) {
        return new LsfErrorResponseWriter(objectMapper, errorResponseFactory);
    }

    @Bean
    @ConditionalOnMissingBean(name = "lsfRequestContextFilterRegistration")
    public FilterRegistrationBean<LsfRequestContextFilter> lsfRequestContextFilterRegistration(
            LsfServiceWebProperties properties
    ) {
        FilterRegistrationBean<LsfRequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LsfRequestContextFilter(properties));
        registration.setName("lsfRequestContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfHttpExceptionHandler lsfHttpExceptionHandler(LsfErrorResponseFactory errorResponseFactory) {
        return new LsfHttpExceptionHandler(errorResponseFactory);
    }
}
