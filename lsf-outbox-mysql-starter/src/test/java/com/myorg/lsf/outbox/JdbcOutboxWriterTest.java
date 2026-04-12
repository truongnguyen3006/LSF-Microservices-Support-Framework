package com.myorg.lsf.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.mysql.JdbcOutboxWriter;
import com.myorg.lsf.outbox.mysql.LsfOutboxMySqlProperties;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

class JdbcOutboxWriterTest {

    private static DataSource newDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:outbox_test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("sa");
        return ds;
    }

    @Test
    void append_shouldInsertOneRow_andReturnId() {
        DataSource ds = newDataSource();

        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:META-INF/spring/lsf/sql/mysql")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(ds);

        LsfOutboxMySqlProperties props = new LsfOutboxMySqlProperties();
        props.setEnabled(true);
        props.setTable("lsf_outbox");

        OutboxWriter writer = new JdbcOutboxWriter(jdbc, new ObjectMapper(), props);

        EventEnvelope env = EventEnvelope.builder()
                .eventId("E_OUTBOX_1")
                .eventType("demo.hello.v1")
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .occurredAtMs(System.currentTimeMillis())
                .producer("test")
                .payload(new ObjectMapper().createObjectNode().put("hello", "world"))
                .build();

        long id = writer.append(env, "demo-topic", "key1");
        assertTrue(id > 0, "id should be generated");

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM lsf_outbox", Integer.class);
        assertEquals(1, count);

        String eventId = jdbc.queryForObject("SELECT event_id FROM lsf_outbox WHERE id = ?", String.class, id);
        assertEquals("E_OUTBOX_1", eventId);

        String status = jdbc.queryForObject("SELECT status FROM lsf_outbox WHERE id = ?", String.class, id);
        assertEquals("NEW", status);
    }
}
