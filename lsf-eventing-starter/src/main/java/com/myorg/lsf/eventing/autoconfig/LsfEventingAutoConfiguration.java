package com.myorg.lsf.eventing.autoconfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.eventing.*;
import com.myorg.lsf.eventing.idempotency.IdempotencyGuard;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import com.myorg.lsf.eventing.idempotency.InMemoryIdempotencyStore;
import com.myorg.lsf.eventing.idempotency.RedisIdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

@Slf4j
@AutoConfiguration(after = LsfEventingRedisAutoConfiguration.class)
@EnableConfigurationProperties(LsfEventingProperties.class)
@ConditionalOnClass(KafkaTemplate.class)
public class LsfEventingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HandlerRegistry handlerRegistry() {
        return new HandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfPublisher lsfPublisher(KafkaTemplate<String, Object> template,
                                     ObjectMapper mapper,
                                     LsfEventingProperties props,
                                     Environment env) {

        String producer = props.getProducerName();
        if (!StringUtils.hasText(producer)) {
            producer = env.getProperty("spring.application.name", "unknown-service");
        }
        return new DefaultLsfPublisher(template, mapper, producer);
    }

    @Bean
    @ConditionalOnMissingBean
    public PayloadConverter payloadConverter(ObjectMapper mapper) {
        return new JacksonPayloadConverter(mapper);
    }


    @Bean
    @ConditionalOnMissingBean(name = "lsfHandlerScanner")
    public Object lsfHandlerScanner(ApplicationContext ctx, HandlerRegistry registry, ObjectMapper mapper) {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(Component.class);

        beans.values().forEach(bean -> {
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            if (targetClass == null) targetClass = bean.getClass();

            Map<Method, LsfEventHandler> methods = MethodIntrospector.selectMethods(
                    targetClass,
                    (Method m) -> AnnotatedElementUtils.findMergedAnnotation(m, LsfEventHandler.class)
            );

            methods.forEach((method, ann) -> {
                // quan trọng: chọn method invocable trên proxy class
                Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
                registry.register(ann.value(), new HandlerMethodInvoker(bean, invocable, ann.payload(), mapper));
            });
        });

        // marker bean để đảm bảo scanner chạy 1 lần
        return new Object();
    }



    @Bean
    @ConditionalOnProperty(prefix = "lsf.eventing.listener", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression(
            "('${lsf.eventing.consume-topics:}'.length() > 0) || ('${lsf.eventing.consume-topics[0]:}'.length() > 0)"
    )
    public LsfEnvelopeListener lsfEnvelopeListener(LsfDispatcher dispatcher, PayloadConverter payloadConverter, LsfEventingProperties props) {
        return new LsfEnvelopeListener(dispatcher, payloadConverter);
    }

    /**
     * Expose consume topics as a {@code String[]} bean for SpEL in {@link org.springframework.kafka.annotation.KafkaListener}.
     *
     * <p>We use a custom condition because Spring Boot does not reliably treat YAML lists as a simple
     * "property present" for {@code @ConditionalOnProperty}.
     */
    @Bean(name = "lsfConsumeTopics")
    @ConditionalOnExpression(
            "('${lsf.eventing.consume-topics:}'.length() > 0) || " +
                    "('${lsf.eventing.consume-topics[0]:}'.length() > 0)"
    )
    public String[] lsfConsumeTopics(LsfEventingProperties props) {
        return props.getConsumeTopics().toArray(String[]::new);
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfDispatcher lsfDispatcher(HandlerRegistry registry,
                                       LsfEventingProperties props,
                                       ObjectProvider<IdempotencyStore> storeProvider) {

        LsfDispatcher base = new DefaultLsfDispatcher(registry, props.isIgnoreUnknownEventType());

        IdempotencyStore store = storeProvider.getIfAvailable();
        if (store != null && props.getIdempotency().isEnabled()) {
            base = new IdempotentLsfDispatcher(base, store);
        }
        return new ContextAwareLsfDispatcher(base);
    }

    // ---------------- Idempotency store auto-configuration ----------------

    /**
     * store=redis but Redis is not on classpath -> fail fast.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "store", havingValue = "redis")
    @ConditionalOnMissingClass("org.springframework.data.redis.connection.RedisConnectionFactory")
    static class MissingRedisDependencyFailFastConfig {
        @Bean
        public Object failFastRedisMissing() {
            throw new IllegalStateException(
                    "lsf.eventing.idempotency.store=redis nhưng thiếu dependency Redis. " +
                            "Hãy thêm spring-boot-starter-data-redis (và cấu hình spring.data.redis.*)."
            );
        }
    }

    /**
     * store=memory OR store=auto without Redis -> fallback to in-memory store.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnExpression("'${lsf.eventing.idempotency.store:auto}'.toLowerCase() != 'redis'")
    static class MemoryFallbackIdempotencyConfig {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public IdempotencyStore idempotencyStore(LsfEventingProperties props, Environment env) {
            var idem = props.getIdempotency();
            String groupId = resolveGroupId(env);
            String prefix = effectiveKeyPrefix(idem.getKeyPrefix(), groupId);
            return new InMemoryIdempotencyStore(
                    prefix,
                    idem.getTtl(),
                    idem.getProcessingTtl(),
                    idem.getMaxEntries(),
                    idem.getCleanupInterval()
            );
        }

    }


    @Bean
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    public IdempotencyGuard idempotencyGuard(
            LsfEventingProperties props,
            org.springframework.core.env.Environment env,
            org.springframework.context.ApplicationContext ctx
    ) {
        return new IdempotencyGuard(props, env, ctx);
    }

    private static String resolveGroupId(Environment env) {
        String gid = env.getProperty("lsf.kafka.consumer.group-id");
        if (!StringUtils.hasText(gid)) {
            gid = env.getProperty("spring.kafka.consumer.group-id");
        }
        if (!StringUtils.hasText(gid)) gid = "default-group";
        // Redis key friendly
        return gid.trim().replaceAll("\\s+", "_");
    }

    private static String effectiveKeyPrefix(String configuredPrefix, String groupId) {
        String base = StringUtils.hasText(configuredPrefix) ? configuredPrefix.trim() : "lsf:dedup";
        if (!base.endsWith(":")) base = base + ":";

        if (base.contains("{groupId}")) {
            String replaced = base.replace("{groupId}", groupId);
            return replaced.endsWith(":") ? replaced : (replaced + ":");
        }
        if (base.endsWith(groupId + ":")) return base;
        return base + groupId + ":";
    }


}
