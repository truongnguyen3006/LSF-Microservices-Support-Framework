package com.myorg.lsf.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.http.LsfErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;

@AutoConfiguration(before = SecurityAutoConfiguration.class)
@EnableConfigurationProperties(LsfSecurityProperties.class)
@ConditionalOnProperty(prefix = "lsf.security", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
public class LsfSecurityAutoConfiguration {

    @Bean
    public SmartLifecycle lsfSecurityStartupValidator(LsfSecurityProperties properties) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                if (properties.getMode() == LsfSecurityProperties.Mode.API_KEY
                        && !StringUtils.hasText(properties.getApiKey().getValue())) {
                    throw new IllegalStateException("lsf.security.mode=API_KEY but lsf.security.api-key.value is blank.");
                }

                if (properties.getMode() == LsfSecurityProperties.Mode.JWT
                        && !StringUtils.hasText(properties.getJwt().getIssuerUri())
                        && !StringUtils.hasText(properties.getJwt().getJwkSetUri())
                        && !StringUtils.hasText(properties.getJwt().getHmacSecret())) {
                    throw new IllegalStateException(
                            "lsf.security.mode=JWT requires one of issuer-uri, jwk-set-uri or hmac-secret."
                    );
                }
                running = true;
            }

            @Override
            public void stop() {
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain lsfSecurityFilterChain(
            HttpSecurity http,
            LsfSecurityProperties properties,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            org.springframework.core.env.Environment environment
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeErrorResponse(
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication is required.",
                                objectMapperProvider.getIfAvailable(),
                                environment.getProperty("spring.application.name", "unknown-service")
                        ))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeErrorResponse(
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Access is denied.",
                                objectMapperProvider.getIfAvailable(),
                                environment.getProperty("spring.application.name", "unknown-service")
                        )));

        http.authorizeHttpRequests(authz -> {
            if (!properties.getPublicPaths().isEmpty()) {
                authz.requestMatchers(toAntMatchers(properties.getPublicPaths())).permitAll();
            }
            if (!properties.getAdminPaths().isEmpty()) {
                authz.requestMatchers(toAntMatchers(properties.getAdminPaths()))
                        .hasAnyAuthority(properties.getAdminAuthorities().toArray(String[]::new));
            }
            authz.anyRequest().authenticated();
        });

        if (properties.getMode() == LsfSecurityProperties.Mode.API_KEY) {
            http.addFilterBefore(
                    new LsfApiKeyAuthenticationFilter(properties.getApiKey()),
                    UsernamePasswordAuthenticationFilter.class
            );
        } else {
            JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
            if (jwtDecoder == null) {
                throw new IllegalStateException("JWT mode is enabled but no JwtDecoder bean was created.");
            }
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                jwt.decoder(jwtDecoder);
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(properties));
            }));
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.security", name = "mode", havingValue = "JWT")
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder lsfJwtDecoder(LsfSecurityProperties properties) {
        if (StringUtils.hasText(properties.getJwt().getIssuerUri())) {
            return JwtDecoders.fromIssuerLocation(properties.getJwt().getIssuerUri());
        }
        if (StringUtils.hasText(properties.getJwt().getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwkSetUri()).build();
        }
        return NimbusJwtDecoder.withSecretKey(secretKey(properties.getJwt().getHmacSecret())).build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter(
            LsfSecurityProperties properties
    ) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                LsfGrantedAuthoritiesExtractor.extract(jwt, properties.getJwt()));
        return converter;
    }

    private static SecretKeySpec secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static RequestMatcher[] toAntMatchers(List<String> patterns) {
        return patterns.stream()
                .filter(StringUtils::hasText)
                .map(AntPathRequestMatcher::new)
                .toArray(RequestMatcher[]::new);
    }

    private static void writeErrorResponse(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String code,
            String message,
            ObjectMapper objectMapper,
            String serviceName
    ) throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }
        if (objectMapper == null) {
            response.sendError(status, message);
            return;
        }

        LsfRequestContext context = requestContext(request);
        if (context != null) {
            if (StringUtils.hasText(context.correlationId())) {
                response.setHeader(CoreHeaders.HTTP_CORRELATION_ID, context.correlationId());
            }
            if (StringUtils.hasText(context.causationId())) {
                response.setHeader(CoreHeaders.HTTP_CAUSATION_ID, context.causationId());
            }
            if (StringUtils.hasText(context.requestId())) {
                response.setHeader(CoreHeaders.HTTP_REQUEST_ID, context.requestId());
            }
        }

        response.setStatus(status);
        response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new LsfErrorResponse(
                Instant.now(),
                status,
                status == HttpServletResponse.SC_UNAUTHORIZED ? "Unauthorized" : "Forbidden",
                code,
                message,
                request.getRequestURI(),
                false,
                serviceName,
                context == null ? null : context.correlationId(),
                context == null ? null : context.causationId(),
                context == null ? null : context.requestId(),
                MDC.get("traceId")
        ));
    }

    private static LsfRequestContext requestContext(jakarta.servlet.http.HttpServletRequest request) {
        Object attribute = request.getAttribute(LsfRequestContext.class.getName());
        if (attribute instanceof LsfRequestContext context) {
            return context;
        }

        String correlationId = firstHeader(request, CoreHeaders.correlationIdHeaders());
        String causationId = firstHeader(request, CoreHeaders.causationIdHeaders());
        String requestId = firstHeader(request, CoreHeaders.requestIdHeaders());
        if (!StringUtils.hasText(correlationId) && !StringUtils.hasText(causationId) && !StringUtils.hasText(requestId)) {
            return null;
        }
        if (!StringUtils.hasText(requestId)) {
            requestId = correlationId;
        }
        return new LsfRequestContext(correlationId, causationId, requestId);
    }

    private static String firstHeader(jakarta.servlet.http.HttpServletRequest request, List<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return null;
        }
        for (String name : names) {
            String value = request.getHeader(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
