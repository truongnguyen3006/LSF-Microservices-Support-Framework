package com.myorg.lsf.quota.autoconfig;

import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaService;
import com.myorg.lsf.quota.config.LsfQuotaProperties;
import com.myorg.lsf.quota.impl.QuotaReservationFacadeImpl;
import com.myorg.lsf.quota.impl.memory.MemoryQuotaService;
import com.myorg.lsf.quota.impl.redis.RedisQuotaService;
import com.myorg.lsf.quota.obs.QuotaMetrics;
import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import com.myorg.lsf.quota.policy.StaticQuotaPolicyProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(LsfQuotaProperties.class)
@ConditionalOnProperty(prefix = "lsf.quota", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfQuotaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QuotaService quotaService(
            LsfQuotaProperties props,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            Environment env
    ) {
        String app = env.getProperty("spring.application.name", "unknown-service");
        String backend = props.getStore().name().toLowerCase();

        QuotaMetrics metrics = null;
        MeterRegistry r = meterRegistryProvider.getIfAvailable();
        if (props.isMetricsEnabled() && r != null) {
            metrics = new QuotaMetrics(r, app, backend);
        }

        Clock clock = Clock.systemUTC();

        return switch (props.getStore()) {
            case REDIS -> {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis == null) {
                    throw new IllegalStateException("lsf.quota.store=redis but no StringRedisTemplate found. Add spring-boot-starter-data-redis.");
                }
                yield new RedisQuotaService(redis, props, metrics, clock);
            }
            case MEMORY -> new MemoryQuotaService(props, metrics, clock);
            case AUTO -> {
                StringRedisTemplate redis = redisProvider.getIfAvailable();
                if (redis != null) yield new RedisQuotaService(redis, props, metrics, clock);
                yield new MemoryQuotaService(props, metrics, clock);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public QuotaPolicyProvider quotaPolicyProvider(LsfQuotaProperties props) {
        return new StaticQuotaPolicyProvider(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public QuotaReservationFacade quotaReservationFacade(QuotaService quotaService, QuotaPolicyProvider policyProvider) {
        return new QuotaReservationFacadeImpl(quotaService, policyProvider);
    }
}