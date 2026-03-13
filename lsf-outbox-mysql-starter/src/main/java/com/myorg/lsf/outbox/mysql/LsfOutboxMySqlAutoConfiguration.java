package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import com.myorg.lsf.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(LsfOutboxMySqlProperties.class)
public class LsfOutboxMySqlAutoConfiguration {

    @Bean(name = "lsfOutboxMySqlClock")
    @ConditionalOnMissingBean(name = "lsfOutboxMySqlClock")
    public Clock lsfOutboxMySqlClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(name = "lsfOutboxObjectMapper")
    public ObjectMapper lsfOutboxObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcOutboxRepository jdbcOutboxRepository(JdbcTemplate jdbc, LsfOutboxMySqlProperties props) {
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
                                     ObjectMapper lsfOutboxObjectMapper,
                                     LsfOutboxMySqlProperties props) {
        return new JdbcOutboxWriter(jdbcTemplate, lsfOutboxObjectMapper, props);
    }

    @Bean(name = "lsfOutboxSchedule")
    public LsfOutboxScheduleValues lsfOutboxScheduleValues(LsfOutboxMySqlProperties props) {
        return new LsfOutboxScheduleValues(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisherHooks outboxPublisherHooks() {
        return new OutboxPublisherHooks() {};
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "lsf.outbox.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean({KafkaTemplate.class, JdbcOutboxRepository.class, MeterRegistry.class})
    public OutboxMetrics outboxMetrics(MeterRegistry registry,
                                       JdbcOutboxRepository repo,
                                       Clock clock) {
        OutboxMetrics m = new OutboxMetrics(registry, repo, clock);
        m.preRegister();
        return m;
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnBean({KafkaTemplate.class, JdbcOutboxRepository.class})
    public OutboxPublisher outboxPublisher(LsfOutboxMySqlProperties props,
                                           JdbcOutboxRepository repo,
                                           KafkaTemplate<String, Object> kafkaTemplate,
                                           ObjectMapper lsfOutboxObjectMapper,
                                           TransactionTemplate lsfOutboxTxTemplate,
                                           @Qualifier("lsfOutboxMySqlClock") Clock clock,
                                           OutboxPublisherHooks hooks,
                                           ObjectProvider<OutboxMetrics> metricsProvider) {
        return new OutboxPublisher(
                props,
                repo,
                kafkaTemplate,
                lsfOutboxObjectMapper,
                lsfOutboxTxTemplate,
                clock,
                hooks,
                metricsProvider.getIfAvailable()
        );
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    static class SchedulingConfig {}
}
