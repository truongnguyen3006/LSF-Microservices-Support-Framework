package com.myorg.lsf.saga;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class SagaSql {

    private static final Pattern TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?$");

    private SagaSql() {
    }

    public static String validateTableName(String table) {
        if (!StringUtils.hasText(table) || !TABLE_NAME.matcher(table.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Invalid table name: " + table + ". Expected table_name or schema.table_name"
            );
        }
        return table.trim();
    }
}
