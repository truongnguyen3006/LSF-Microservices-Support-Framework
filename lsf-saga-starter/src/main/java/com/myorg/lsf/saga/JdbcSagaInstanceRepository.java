package com.myorg.lsf.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcSagaInstanceRepository implements SagaInstanceRepository {

    private static final TypeReference<List<SagaInstanceStep>> STEP_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LsfSagaProperties properties;

    public JdbcSagaInstanceRepository(JdbcTemplate jdbc, ObjectMapper mapper, LsfSagaProperties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public SagaInstance create(SagaInstance instance) {
        String sql = """
                INSERT INTO %s
                (saga_id, definition_name, status, phase, current_step_index, compensation_step_index, current_step,
                 correlation_id, request_id, causation_id, last_event_id, failure_reason,
                 state_json, steps_json, next_timeout_at_ms, created_at_ms, updated_at_ms, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table());

        jdbc.update(sql,
                instance.getSagaId(),
                instance.getDefinitionName(),
                instance.getStatus().name(),
                instance.getPhase().name(),
                instance.getCurrentStepIndex(),
                instance.getCompensationStepIndex(),
                instance.getCurrentStep(),
                instance.getCorrelationId(),
                instance.getRequestId(),
                instance.getCausationId(),
                instance.getLastEventId(),
                instance.getFailureReason(),
                toJson(instance.getStateData()),
                toJson(instance.getSteps()),
                instance.getNextTimeoutAtMs(),
                instance.getCreatedAtMs(),
                instance.getUpdatedAtMs(),
                0L
        );
        SagaInstance saved = instance.copy();
        saved.setVersion(0L);
        return saved;
    }

    @Override
    public SagaInstance save(SagaInstance instance) {
        String sql = """
                UPDATE %s
                SET status = ?,
                    phase = ?,
                    current_step_index = ?,
                    compensation_step_index = ?,
                    current_step = ?,
                    correlation_id = ?,
                    request_id = ?,
                    causation_id = ?,
                    last_event_id = ?,
                    failure_reason = ?,
                    state_json = ?,
                    steps_json = ?,
                    next_timeout_at_ms = ?,
                    updated_at_ms = ?,
                    version = ?
                WHERE saga_id = ? AND version = ?
                """.formatted(table());

        long nextVersion = instance.getVersion() + 1;
        int updated = jdbc.update(sql,
                instance.getStatus().name(),
                instance.getPhase().name(),
                instance.getCurrentStepIndex(),
                instance.getCompensationStepIndex(),
                instance.getCurrentStep(),
                instance.getCorrelationId(),
                instance.getRequestId(),
                instance.getCausationId(),
                instance.getLastEventId(),
                instance.getFailureReason(),
                toJson(instance.getStateData()),
                toJson(instance.getSteps()),
                instance.getNextTimeoutAtMs(),
                instance.getUpdatedAtMs(),
                nextVersion,
                instance.getSagaId(),
                instance.getVersion()
        );
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Stale saga version for " + instance.getSagaId());
        }

        SagaInstance saved = instance.copy();
        saved.setVersion(nextVersion);
        return saved;
    }

    @Override
    public Optional<SagaInstance> findById(String sagaId) {
        String sql = "SELECT * FROM " + table() + " WHERE saga_id = ?";
        List<SagaInstance> rows = jdbc.query(sql, this::mapRow, sagaId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<SagaInstance> findActiveByCorrelationId(String correlationId) {
        String sql = """
                SELECT * FROM %s
                WHERE correlation_id = ? AND status IN ('RUNNING', 'WAITING', 'COMPENSATING')
                ORDER BY updated_at_ms DESC
                LIMIT 1
                """.formatted(table());
        List<SagaInstance> rows = jdbc.query(sql, this::mapRow, correlationId);
        return rows.stream().findFirst();
    }

    @Override
    public List<SagaInstance> findDueTimeouts(long nowMs, int limit) {
        String sql = """
                SELECT * FROM %s
                WHERE next_timeout_at_ms IS NOT NULL
                  AND next_timeout_at_ms <= ?
                  AND status IN ('WAITING', 'COMPENSATING')
                ORDER BY next_timeout_at_ms ASC
                LIMIT ?
                """.formatted(table());
        return jdbc.query(sql, this::mapRow, nowMs, Math.max(1, limit));
    }

    private SagaInstance mapRow(ResultSet rs, int rowNum) throws SQLException {
        SagaInstance instance = new SagaInstance();
        instance.setSagaId(rs.getString("saga_id"));
        instance.setDefinitionName(rs.getString("definition_name"));
        instance.setStatus(SagaStatus.valueOf(rs.getString("status")));
        instance.setPhase(SagaPhase.valueOf(rs.getString("phase")));
        instance.setCurrentStepIndex((Integer) rs.getObject("current_step_index"));
        instance.setCompensationStepIndex((Integer) rs.getObject("compensation_step_index"));
        instance.setCurrentStep(rs.getString("current_step"));
        instance.setCorrelationId(rs.getString("correlation_id"));
        instance.setRequestId(rs.getString("request_id"));
        instance.setCausationId(rs.getString("causation_id"));
        instance.setLastEventId(rs.getString("last_event_id"));
        instance.setFailureReason(rs.getString("failure_reason"));
        instance.setStateData(readJsonNode(rs.getString("state_json")));
        instance.setSteps(readSteps(rs.getString("steps_json")));
        instance.setNextTimeoutAtMs((Long) rs.getObject("next_timeout_at_ms"));
        instance.setCreatedAtMs((Long) rs.getObject("created_at_ms"));
        instance.setUpdatedAtMs((Long) rs.getObject("updated_at_ms"));
        instance.setVersion(rs.getLong("version"));
        return instance;
    }

    private JsonNode readJsonNode(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize saga state JSON", ex);
        }
    }

    private List<SagaInstanceStep> readSteps(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, STEP_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize saga step JSON", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize saga JSON", ex);
        }
    }

    private String table() {
        return SagaSql.validateTableName(properties.getJdbc().getTable());
    }
}
