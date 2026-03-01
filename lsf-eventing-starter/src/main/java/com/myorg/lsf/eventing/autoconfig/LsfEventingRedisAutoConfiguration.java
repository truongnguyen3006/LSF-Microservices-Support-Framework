package com.myorg.lsf.eventing.autoconfig;

import com.myorg.lsf.eventing.LsfEventingProperties;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import com.myorg.lsf.eventing.idempotency.RedisIdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Redis-specific auto configuration for idempotency.
 *
 * <p>This class is separated from {@link LsfEventingAutoConfiguration} so that
 * applications that do not include Redis dependencies can still use the starter
 * (e.g., store=memory).
 */
@Slf4j
@AutoConfiguration(before = LsfEventingAutoConfiguration.class)
@EnableConfigurationProperties(LsfEventingProperties.class)
@ConditionalOnClass(RedisConnectionFactory.class)
public class LsfEventingRedisAutoConfiguration {

    /**
     * store=redis: if the app (or Spring Boot) did not create a RedisConnectionFactory, create a minimal
     * standalone LettuceConnectionFactory from spring.data.redis.*.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "store", havingValue = "redis")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(LettuceConnectionFactory.class)
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    static class EnsureRedisConnectionFactoryConfig {

        @Bean
        public RedisConnectionFactory redisConnectionFactory(Environment env) {
            String host = env.getProperty("spring.data.redis.host", "localhost");
            int port = Integer.parseInt(env.getProperty("spring.data.redis.port", "6379"));
            int db = Integer.parseInt(env.getProperty("spring.data.redis.database", "0"));
            String username = env.getProperty("spring.data.redis.username");
            String password = env.getProperty("spring.data.redis.password");

            log.warn("No RedisConnectionFactory bean found; creating LettuceConnectionFactory using spring.data.redis.* (standalone)");

            RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
            cfg.setDatabase(db);
            if (StringUtils.hasText(username)) {
                cfg.setUsername(username);
            }
            if (StringUtils.hasText(password)) {
                cfg.setPassword(RedisPassword.of(password));
            }
            return new LettuceConnectionFactory(cfg);
        }
    }

    /**
     * store=redis: create Redis-backed IdempotencyStore.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "store", havingValue = "redis")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class StrictRedisIdempotencyConfig {

        @Bean
        @ConditionalOnMissingBean(StringRedisTemplate.class)
        public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
            return new StringRedisTemplate(cf);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public IdempotencyStore idempotencyStore(LsfEventingProperties props, StringRedisTemplate redis, Environment env) {
            var idem = props.getIdempotency();
            if (!idem.getRedis().isEnabled()) {
                throw new IllegalStateException("store=redis nhưng lsf.eventing.idempotency.redis.enabled=false");
            }

            String groupId = resolveGroupId(env);
            String prefix = effectiveKeyPrefix(idem.getRedis().getKeyPrefix(), groupId);

            return new RedisIdempotencyStore(redis, idem.getTtl(), idem.getProcessingTtl(), prefix);
        }

    }

    /**
     * store=auto: if Redis is available (RedisConnectionFactory bean exists), use Redis store.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "lsf.eventing.idempotency.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(RedisConnectionFactory.class)
    static class AutoRedisIdempotencyConfig {

        @Bean
        @ConditionalOnMissingBean(StringRedisTemplate.class)
        public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
            return new StringRedisTemplate(cf);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public IdempotencyStore idempotencyStore(LsfEventingProperties props, StringRedisTemplate redis, Environment env) {
            var idem = props.getIdempotency();
            String groupId = resolveGroupId(env);
            String prefix = effectiveKeyPrefix(idem.getRedis().getKeyPrefix(), groupId);
            return new RedisIdempotencyStore(redis, idem.getTtl(), idem.getProcessingTtl(), prefix);
        }

    }

    private static String resolveGroupId(Environment env) {
        String gid = env.getProperty("lsf.kafka.consumer.group-id");
        if (!StringUtils.hasText(gid)) {
            gid = env.getProperty("spring.kafka.consumer.group-id");
        }
        if (!StringUtils.hasText(gid)) gid = "default-group";
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
