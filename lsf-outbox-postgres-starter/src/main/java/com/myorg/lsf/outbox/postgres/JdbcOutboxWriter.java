package com.myorg.lsf.outbox.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import com.myorg.lsf.outbox.sql.OutboxSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
@Slf4j
@RequiredArgsConstructor
public class JdbcOutboxWriter implements OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LsfOutboxPostgresProperties props;
    private final OutboxMetrics metrics;
    private String t() {
        return OutboxSql.validateTableName(props.getTable());
    }

    public JdbcOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, LsfOutboxPostgresProperties props) {
        this(jdbc, mapper, props, null);
    }

    @Override
    public long append(EventEnvelope envelope, String topic, String key) {
        if (envelope == null) throw new IllegalArgumentException("envelope must not be null");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");

        String json = toJson(envelope);

        String sql = """
            INSERT INTO %s
              (topic, msg_key, event_id, event_type, correlation_id, aggregate_id, envelope_json)
            VALUES
              (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            RETURNING id
            """.formatted(t());

        Long id = jdbc.queryForObject(sql, Long.class,
                topic,
                key,
                envelope.getEventId(),
                envelope.getEventType(),
                envelope.getCorrelationId(),
                envelope.getAggregateId(),
                json
        );

        if (id == null) throw new IllegalStateException("No id returned from insert");

        if (metrics != null) metrics.incAppend();

        log.debug(
                "OUTBOX APPEND -> id={}, eventId={}, eventType={}, topic={}, aggregateId={}, correlationId={}",
                id,
                envelope.getEventId(),
                envelope.getEventType(),
                topic,
                envelope.getAggregateId(),
                envelope.getCorrelationId()
        );

        return id;
    }

    private String toJson(EventEnvelope env) {
        try {
            return mapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EventEnvelope", e);
        }
    }
}