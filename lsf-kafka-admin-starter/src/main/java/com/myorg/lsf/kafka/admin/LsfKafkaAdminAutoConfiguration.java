package com.myorg.lsf.kafka.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.kafka.KafkaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass({AdminClient.class, KafkaTemplate.class})
@EnableConfigurationProperties(LsfKafkaAdminProperties.class)
@ConditionalOnProperty(prefix = "lsf.kafka.admin", name = "enabled", havingValue = "true")
public class LsfKafkaAdminAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AdminClient lsfKafkaAdminClient(KafkaProperties properties) {
        if (!StringUtils.hasText(properties.getBootstrapServers())) {
            throw new IllegalStateException("lsf.kafka.bootstrap-servers must be configured for lsf-kafka-admin-starter.");
        }
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        return AdminClient.create(configs);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public LsfKafkaReplayMetrics lsfKafkaReplayMetrics(
            MeterRegistry registry,
            Environment environment
    ) {
        String service = environment.getProperty("spring.application.name", "unknown-service");
        LsfKafkaReplayMetrics metrics = new LsfKafkaReplayMetrics(registry, service);
        metrics.preRegister();
        return metrics;
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfKafkaDlqService lsfKafkaDlqService(
            AdminClient adminClient,
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            LsfKafkaAdminProperties properties,
            ObjectProvider<LsfKafkaReplayMetrics> metricsProvider
    ) {
        return new LsfKafkaDlqService(
                adminClient,
                consumerFactory,
                kafkaTemplate,
                objectMapper,
                properties,
                metricsProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public LsfKafkaDlqController lsfKafkaDlqController(LsfKafkaDlqService service) {
        return new LsfKafkaDlqController(service);
    }
}
