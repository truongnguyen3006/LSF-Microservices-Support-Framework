package com.myorg.lsf.outbox.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.outbox.OutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@AutoConfiguration(after = { DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class })
@EnableConfigurationProperties(LsfOutboxPostgresProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
public class LsfOutboxPostgresAutoConfiguration {

    @Bean(name = "lsfOutboxObjectMapper")
    @ConditionalOnMissingBean(name = "lsfOutboxObjectMapper")
    public ObjectMapper lsfOutboxObjectMapper(
            org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder
    ) {
        return builder
                .createXmlMapper(false)
                .build()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock lsfOutboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public JdbcOutboxRepository jdbcOutboxRepository(JdbcTemplate jdbc, LsfOutboxPostgresProperties props) {
        return new JdbcOutboxRepository(jdbc, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate lsfOutboxTxTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lsf.outbox", name = "enabled", havingValue = "true")
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate,
                                     @Qualifier("lsfOutboxObjectMapper") ObjectMapper lsfOutboxObjectMapper,
                                     LsfOutboxPostgresProperties props,
                                     ObjectProvider<OutboxMetrics> metricsProvider) {
        return new JdbcOutboxWriter(
                jdbcTemplate,
                lsfOutboxObjectMapper,
                props,
                metricsProvider.getIfAvailable()
        );
    }

    @Bean(name = "lsfOutboxSchedule")
    public LsfOutboxScheduleValues lsfOutboxScheduleValues(LsfOutboxPostgresProperties props) {
        return new LsfOutboxScheduleValues(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisherHooks outboxPublisherHooks() {
        return new OutboxPublisherHooks() {};
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.outbox.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({ KafkaTemplate.class, JdbcOutboxRepository.class, MeterRegistry.class })
    public OutboxMetrics outboxMetrics(MeterRegistry registry,
                                       JdbcOutboxRepository repo,
                                       @Qualifier("lsfOutboxPostgresClock") Clock clock,
                                       org.springframework.core.env.Environment env,
                                       LsfOutboxPostgresProperties props) {
        String service = env.getProperty("spring.application.name", "unknown-service");
        OutboxMetrics m = new OutboxMetrics(registry, repo, clock, service, props.getTable());
        m.preRegister();
        return m;
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnBean({ KafkaTemplate.class, JdbcOutboxRepository.class })
    public OutboxPublisher outboxPublisher(LsfOutboxPostgresProperties props,
                                           JdbcOutboxRepository repo,
                                           KafkaTemplate<String, Object> kafkaTemplate,
                                           ObjectMapper lsfOutboxObjectMapper,
                                           TransactionTemplate lsfOutboxTxTemplate,
                                           Clock lsfOutboxClock,
                                           OutboxPublisherHooks hooks,
                                           ObjectProvider<OutboxMetrics> metricsProvider) {
        return new OutboxPublisher(
                props, repo, kafkaTemplate, lsfOutboxObjectMapper, lsfOutboxTxTemplate, lsfOutboxClock, hooks,
                metricsProvider.getIfAvailable()
        );
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    static class SchedulingConfig {}
}