package com.myorg.lsf.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class LsfJwtDecoderAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class, LsfSecurityAutoConfiguration.class));

    @Test
    void shouldCreateJwtDecoderFromHmacSecret() {
        runner.withPropertyValues(
                        "lsf.security.enabled=true",
                        "lsf.security.mode=jwt",
                        "lsf.security.jwt.hmac-secret=01234567890123456789012345678901"
                )
                .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
    }
}
