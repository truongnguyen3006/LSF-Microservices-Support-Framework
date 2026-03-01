package com.myorg.lsf.outbox.sql;

/**
 * Small SQL-related helpers used by LSF Outbox modules.
 *
 * <p>We validate table identifiers because the outbox modules embed the table name into SQL
 * statements (identifier position cannot be parameterized). We therefore restrict it to a safe
 * identifier format.
 */
public final class OutboxSql {

    private OutboxSql() {}

    /**
     * Validate and normalize the configured outbox table name.
     *
     * <p>Allowed formats:
     * <ul>
     *   <li>{@code table_name}</li>
     *   <li>{@code schema.table_name} (useful for Postgres; also acceptable for MySQL)</li>
     * </ul>
     *
     * @return trimmed table name
     * @throws IllegalArgumentException if invalid
     */
    public static String validateTableName(String table) {
        if (table == null) {
            throw new IllegalArgumentException("lsf.outbox.table is null");
        }
        String t = table.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("lsf.outbox.table is blank");
        }
        // Keep it strict: only letters, digits, underscore; optional schema prefix.
        if (!t.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) {
            throw new IllegalArgumentException("Invalid lsf.outbox.table: " + table);
        }
        return t;
    }
}