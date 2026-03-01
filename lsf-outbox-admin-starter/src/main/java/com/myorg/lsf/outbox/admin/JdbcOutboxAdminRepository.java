package com.myorg.lsf.outbox.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class JdbcOutboxAdminRepository {

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;
    private final String table;

    public List<OutboxAdminRow> list(List<OutboxStatus> statuses, int limit, int offset) {
        String sql = """
                SELECT id, topic, msg_key, event_id, event_type, status,
                       created_at, sent_at, retry_count, last_error,
                       lease_owner, lease_until, next_attempt_at
                FROM %s
                WHERE (:statusesEmpty = true OR status IN (:statuses))
                ORDER BY id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(table);

        boolean empty = (statuses == null || statuses.isEmpty());
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("statusesEmpty", empty)
                .addValue("statuses", empty ? List.of("NEW") : statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit)
                .addValue("offset", offset);

        return named.query(sql, p, (rs, i) -> new OutboxAdminRow(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("msg_key"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                OutboxStatus.from(rs.getString("status")),
                rs.getInt("retry_count"),
                tsToInstant(rs.getTimestamp("created_at")),
                tsToInstant(rs.getTimestamp("sent_at")),
                tsToInstant(rs.getTimestamp("next_attempt_at")),
                tsToInstant(rs.getTimestamp("lease_until")),
                rs.getString("lease_owner"),
                rs.getString("last_error")
        ));
    }

    public Optional<OutboxAdminRow> findByEventId(String eventId) {
        String sql = """
                SELECT id, topic, msg_key, event_id, event_type, status,
                       created_at, sent_at, retry_count, last_error,
                       lease_owner, lease_until, next_attempt_at
                FROM %s
                WHERE event_id = ?
                """.formatted(table);

        List<OutboxAdminRow> rows = jdbc.query(sql, (rs, i) -> new OutboxAdminRow(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("msg_key"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                OutboxStatus.from(rs.getString("status")),
                rs.getInt("retry_count"),
                tsToInstant(rs.getTimestamp("created_at")),
                tsToInstant(rs.getTimestamp("sent_at")),
                tsToInstant(rs.getTimestamp("next_attempt_at")),
                tsToInstant(rs.getTimestamp("lease_until")),
                rs.getString("lease_owner"),
                rs.getString("last_error")
        ), eventId);

        return rows.stream().findFirst();
    }

    /** mode=NEW => chạy ngay, mode=RETRY => set next_attempt_at=now */
    public int requeueByEventId(String eventId, OutboxStatus mode, boolean resetRetry, Instant now) {
        if (mode != OutboxStatus.NEW && mode != OutboxStatus.RETRY) {
            throw new IllegalArgumentException("mode must be NEW or RETRY");
        }

        String sql = """
                UPDATE %s
                SET status = :status,
                    next_attempt_at = :nextAttemptAt,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_error = NULL,
                    retry_count = CASE WHEN :resetRetry THEN 0 ELSE retry_count END
                WHERE event_id = :eventId
                """.formatted(table);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", mode.name())
                .addValue("nextAttemptAt", mode == OutboxStatus.RETRY ? Timestamp.from(now) : null)
                .addValue("resetRetry", resetRetry)
                .addValue("eventId", eventId);

        return named.update(sql, p);
    }

    /** Portable MySQL+Postgres: select ids then update */
    public int requeueFailed(int limit, boolean resetRetry, Instant now) {
        String select = """
                SELECT id
                FROM %s
                WHERE status = 'FAILED'
                ORDER BY id
                LIMIT :limit
                """.formatted(table);

        List<Long> ids = named.queryForList(select, Map.of("limit", limit), Long.class);
        if (ids.isEmpty()) return 0;

        String update = """
                UPDATE %s
                SET status = 'RETRY',
                    next_attempt_at = :now,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_error = NULL,
                    retry_count = CASE WHEN :resetRetry THEN 0 ELSE retry_count END
                WHERE id IN (:ids)
                """.formatted(table);

        return named.update(update, Map.of(
                "ids", ids,
                "now", Timestamp.from(now),
                "resetRetry", resetRetry
        ));
    }

    public int markFailedByEventId(String eventId, String error) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED',
                    last_error = :error,
                    lease_owner = NULL,
                    lease_until = NULL
                WHERE event_id = :eventId
                """.formatted(table);

        return named.update(sql, Map.of("eventId", eventId, "error", safeErr(error)));
    }

    public int deleteByEventId(String eventId) {
        String sql = "DELETE FROM %s WHERE event_id = ?".formatted(table);
        return jdbc.update(sql, eventId);
    }

    private static Instant tsToInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String safeErr(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.length() > 2000 ? v.substring(0, 2000) : v;
    }
}