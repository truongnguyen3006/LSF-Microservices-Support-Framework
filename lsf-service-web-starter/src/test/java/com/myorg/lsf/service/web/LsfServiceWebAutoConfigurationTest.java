package com.myorg.lsf.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class LsfServiceWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LsfServiceWebAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldCreateWebConventionsBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LsfErrorResponseFactory.class);
            assertThat(context).hasSingleBean(LsfErrorResponseWriter.class);
            assertThat(context).hasSingleBean(LsfHttpExceptionHandler.class);
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
    }
}
