package com.myorg.lsf.outbox.admin;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresOutboxAdminRepositoryIT extends AbstractOutboxAdminVendorITSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lsf_outbox_admin")
            .withUsername("test")
            .withPassword("test");

    @Override
    protected String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    @Override
    protected String username() {
        return POSTGRES.getUsername();
    }

    @Override
    protected String password() {
        return POSTGRES.getPassword();
    }

    @Override
    protected String[] flywayLocations() {
        return new String[]{"classpath:db/migration"};
    }

    @Override
    protected String envelopePlaceholder() {
        return "CAST(? AS JSONB)";
    }

    @Override
    protected void clearTable() {
        jdbc.execute("TRUNCATE TABLE lsf_outbox RESTART IDENTITY");
    }
}
