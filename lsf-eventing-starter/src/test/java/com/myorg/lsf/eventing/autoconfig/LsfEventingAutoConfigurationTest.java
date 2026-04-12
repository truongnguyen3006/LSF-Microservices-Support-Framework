package com.myorg.lsf.eventing.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import com.myorg.lsf.eventing.idempotency.InMemoryIdempotencyStore;
import com.myorg.lsf.eventing.idempotency.RedisIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LsfEventingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LsfEventingRedisAutoConfiguration.class,
                    LsfEventingAutoConfiguration.class
            ))
            .withUserConfiguration(BaseTestConfig.class);

    @Test
    void shouldCreateListenerAndConsumeTopicsBeanWhenTopicsConfigured() {
        runner.withPropertyValues("lsf.eventing.consume-topics[0]=orders.events")
                .run(context -> {
                    assertThat(context).hasSingleBean(LsfEnvelopeListener.class);
                    assertThat(context.getBean("lsfConsumeTopics", String[].class))
                            .containsExactly("orders.events");
                });
    }

    @Test
    void shouldNotCreateListenerWhenConsumeTopicsAreMissing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(LsfEnvelopeListener.class);
            assertThat(context).doesNotHaveBean("lsfConsumeTopics");
        });
    }

    @Test
    void shouldFallbackToInMemoryStoreWhenIdempotencyEnabledWithoutRedis() {
        runner.withPropertyValues(
                        "lsf.eventing.idempotency.enabled=true",
                        "lsf.eventing.idempotency.store=memory",
                        "lsf.kafka.consumer.group-id=payments-group"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context.getBean(IdempotencyStore.class)).isInstanceOf(InMemoryIdempotencyStore.class);
                });
    }

    @Test
    void shouldUseRedisStoreWhenAutoModeAndRedisConnectionFactoryExists() {
        runner.withPropertyValues(
                        "lsf.eventing.idempotency.enabled=true",
                        "lsf.eventing.idempotency.store=auto",
                        "lsf.kafka.consumer.group-id=payments-group"
                )
                .withUserConfiguration(RedisConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context.getBean(IdempotencyStore.class)).isInstanceOf(RedisIdempotencyStore.class);
                });
    }

    @Test
    void shouldFailFastWhenStoreRedisButRedisDependencyIsMissing() {
        runner.withPropertyValues(
                        "lsf.eventing.idempotency.enabled=true",
                        "lsf.eventing.idempotency.store=redis"
                )
                .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("thiếu dependency Redis");
                });
    }

    @Configuration
    static class BaseTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        LsfPublisher stubPublisher() {
            return (topic, key, eventType, aggregateId, payload) -> CompletableFuture.completedFuture(null);
        }
    }

    @Configuration
    static class RedisConfig {

        @Bean(destroyMethod = "destroy")
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", 6379);
        }
    }
}
