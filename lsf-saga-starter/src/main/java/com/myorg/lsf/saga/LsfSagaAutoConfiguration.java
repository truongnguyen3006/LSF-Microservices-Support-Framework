package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.outbox.OutboxWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;

@AutoConfiguration(after = {
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class
})
@EnableConfigurationProperties(LsfSagaProperties.class)
@ConditionalOnProperty(prefix = "lsf.saga", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LsfSagaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SagaDefinitionRegistry sagaDefinitionRegistry(ObjectProvider<SagaDefinition<?>> definitions) {
        return new SagaDefinitionRegistry(definitions.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(name = "lsfSagaClock")
    public Clock lsfSagaClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper lsfSagaObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "lsf.saga", name = "store", havingValue = "jdbc")
    public SagaInstanceRepository jdbcSagaInstanceRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            LsfSagaProperties properties
    ) {
        return new JdbcSagaInstanceRepository(jdbcTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "lsf.saga", name = "store", havingValue = "auto", matchIfMissing = true)
    public SagaInstanceRepository autoSagaInstanceRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            LsfSagaProperties properties
    ) {
        return new JdbcSagaInstanceRepository(jdbcTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaInstanceRepository inMemorySagaInstanceRepository() {
        return new InMemorySagaInstanceRepository();
    }

    @Bean
    @ConditionalOnBean({DataSource.class, SagaInstanceRepository.class})
    public SmartInitializingSingleton lsfSagaJdbcSchemaInitializer(
            DataSource dataSource,
            LsfSagaProperties properties,
            SagaInstanceRepository repository
    ) {
        return () -> {
            if (!(repository instanceof JdbcSagaInstanceRepository)) {
                return;
            }
            if (!shouldInitializeSchema(dataSource, properties.getJdbc().getInitializeSchema())) {
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("META-INF/spring/lsf/sql/lsf_saga.sql")
            );
            DatabasePopulatorUtils.execute(populator, dataSource);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaEventPublisher sagaEventPublisher(
            ObjectProvider<OutboxWriter> outboxWriterProvider,
            ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
            LsfSagaProperties properties
    ) {
        return new DefaultSagaEventPublisher(outboxWriterProvider, kafkaTemplateProvider, properties);
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    @ConditionalOnMissingBean(name = "lsfSagaTransactionTemplate")
    public TransactionTemplate lsfSagaTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfSagaOrchestrator lsfSagaOrchestrator(
            SagaDefinitionRegistry sagaDefinitionRegistry,
            SagaInstanceRepository repository,
            SagaEventPublisher publisher,
            ObjectMapper objectMapper,
            Clock lsfSagaClock,
            LsfSagaProperties properties,
            Environment environment,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider
    ) {
        String producer = environment.getProperty("spring.application.name", "unknown-service");
        return new DefaultLsfSagaOrchestrator(
                sagaDefinitionRegistry,
                repository,
                publisher,
                objectMapper,
                lsfSagaClock,
                properties,
                producer,
                transactionTemplateProvider.getIfAvailable()
        );
    }

    @Bean(name = "lsfSagaTimeoutScannerDelayMs")
    public Long lsfSagaTimeoutScannerDelayMs(LsfSagaProperties properties) {
        return properties.getTimeoutScanner().getPollInterval().toMillis();
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.saga.timeout-scanner", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SagaTimeoutScheduler sagaTimeoutScheduler(LsfSagaOrchestrator orchestrator) {
        return new SagaTimeoutScheduler(orchestrator);
    }

    @Bean
    @ConditionalOnClass(LsfDispatcher.class)
    public static BeanPostProcessor lsfSagaDispatcherBeanPostProcessor(
            LsfSagaOrchestrator orchestrator,
            LsfSagaProperties properties
    ) {
        return new SagaDispatcherBeanPostProcessor(orchestrator, properties);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "lsf.saga.timeout-scanner", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }

    @Configuration
    @ConditionalOnProperty(prefix = "lsf.saga", name = "store", havingValue = "jdbc")
    static class JdbcFailFastConfig {
        @Bean
        public SmartInitializingSingleton failFastMissingDataSource(
                ObjectProvider<DataSource> dataSourceProvider
        ) {
            return () -> {
                if (dataSourceProvider.getIfAvailable() == null) {
                    throw new IllegalStateException("lsf.saga.store=jdbc but no DataSource bean was found.");
                }
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "lsf.saga.transport", name = "mode", havingValue = "outbox")
    static class OutboxTransportFailFastConfig {
        @Bean
        public SmartInitializingSingleton failFastMissingOutboxWriter(
                ObjectProvider<OutboxWriter> outboxWriterProvider
        ) {
            return () -> {
                if (outboxWriterProvider.getIfAvailable() == null) {
                    throw new IllegalStateException("lsf.saga.transport.mode=outbox but no OutboxWriter bean was found.");
                }
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "lsf.saga.transport", name = "mode", havingValue = "direct")
    static class DirectTransportFailFastConfig {
        @Bean
        public SmartInitializingSingleton failFastMissingKafkaTemplate(
                ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider
        ) {
            return () -> {
                if (kafkaTemplateProvider.getIfAvailable() == null) {
                    throw new IllegalStateException("lsf.saga.transport.mode=direct but no KafkaTemplate bean was found.");
                }
            };
        }
    }

    private boolean shouldInitializeSchema(DataSource dataSource, SagaSchemaInitialization mode) {
        if (mode == SagaSchemaInitialization.NEVER) {
            return false;
        }
        if (mode == SagaSchemaInitialization.ALWAYS) {
            return true;
        }
        return isEmbedded(dataSource);
    }

    private boolean isEmbedded(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (!StringUtils.hasText(url)) {
                return false;
            }
            String lower = url.toLowerCase();
            return lower.startsWith("jdbc:h2:") || lower.startsWith("jdbc:hsqldb:") || lower.startsWith("jdbc:derby:");
        } catch (Exception ex) {
            return false;
        }
    }
}
