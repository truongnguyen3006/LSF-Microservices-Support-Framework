package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import com.myorg.lsf.outbox.sql.OutboxSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
@Slf4j
@RequiredArgsConstructor
public class JdbcOutboxWriter implements OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LsfOutboxMySqlProperties props;
    private final OutboxMetrics metrics;
    private String t() {
        return OutboxSql.validateTableName(props.getTable());
    }

    public JdbcOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, LsfOutboxMySqlProperties props) {
        this(jdbc, mapper, props, null);
    }

    @Override
    public long append(EventEnvelope envelope, String topic, String key) {
        if (envelope == null) throw new IllegalArgumentException("envelope must not be null");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");

        String envelopeJson = toJson(envelope);

        String sql = "INSERT INTO " + t() +
                " (topic, msg_key, event_id, event_type, correlation_id, aggregate_id, envelope_json)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            // ask only for the 'id' column key (fixes H2 returning multiple keys)
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, topic);
            ps.setString(2, key);
            ps.setString(3, envelope.getEventId());
            ps.setString(4, envelope.getEventType());
            ps.setString(5, envelope.getCorrelationId());
            ps.setString(6, envelope.getAggregateId());
            ps.setString(7, envelopeJson);
            return ps;
        }, kh);

        Number k = kh.getKey();
        Long outboxId = null;

        if (k != null) outboxId = k.longValue();

        if (outboxId == null) {
            var keys = kh.getKeys();
            if (keys != null) {
                Object id = keys.get("id");
                if (id == null) id = keys.get("ID");
                if (id instanceof Number n) outboxId = n.longValue();
            }
        }

        if (outboxId == null) {
            throw new IllegalStateException("No generated key 'id' returned from insert into " + t());
        }

        if (metrics != null) metrics.incAppend();

        log.debug(
                "OUTBOX APPEND -> id={}, eventId={}, eventType={}, topic={}, aggregateId={}, correlationId={}",
                outboxId,
                envelope.getEventId(),
                envelope.getEventType(),
                topic,
                envelope.getAggregateId(),
                envelope.getCorrelationId()
        );

        return outboxId;
    }


    private String toJson(EventEnvelope env) {
        try {
            return mapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EventEnvelope to JSON", e);
        }
    }
}
