package com.myorg.lsf.outbox.admin;

import com.myorg.lsf.outbox.sql.OutboxSql;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(LsfOutboxAdminProperties.class)
@ConditionalOnProperty(prefix = "lsf.outbox.admin", name = "enabled", havingValue = "true")
@ConditionalOnClass(JdbcTemplate.class)
public class LsfOutboxAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock lsfOutboxAdminClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public NamedParameterJdbcTemplate lsfOutboxNamedJdbc(JdbcTemplate jdbc) {
        return new NamedParameterJdbcTemplate(jdbc.getDataSource());
    }

    @Bean
    public JdbcOutboxAdminRepository outboxAdminRepository(NamedParameterJdbcTemplate named,
                                                           JdbcTemplate jdbc,
                                                           Environment env) {
        String table = env.getProperty("lsf.outbox.table", "lsf_outbox");
        table = OutboxSql.validateTableName(table);
        return new JdbcOutboxAdminRepository(named, jdbc, table);
    }

    @Bean
    public OutboxAdminService outboxAdminService(JdbcOutboxAdminRepository repo,
                                                 LsfOutboxAdminProperties props,
                                                 Clock lsfOutboxAdminClock) {
        return new OutboxAdminService(repo, props, lsfOutboxAdminClock);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public OutboxAdminController outboxAdminController(OutboxAdminService svc) {
        return new OutboxAdminController(svc);
    }

}