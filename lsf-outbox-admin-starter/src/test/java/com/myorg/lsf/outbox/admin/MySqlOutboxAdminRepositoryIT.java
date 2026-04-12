package com.myorg.lsf.outbox.admin;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MySqlOutboxAdminRepositoryIT extends AbstractOutboxAdminVendorITSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lsf_outbox_admin")
            .withUsername("test")
            .withPassword("test");

    @Override
    protected String jdbcUrl() {
        return MYSQL.getJdbcUrl();
    }

    @Override
    protected String username() {
        return MYSQL.getUsername();
    }

    @Override
    protected String password() {
        return MYSQL.getPassword();
    }

    @Override
    protected String[] flywayLocations() {
        return new String[]{"classpath:META-INF/spring/lsf/sql/mysql"};
    }

    @Override
    protected String envelopePlaceholder() {
        return "?";
    }

    @Override
    protected void clearTable() {
        jdbc.execute("TRUNCATE TABLE lsf_outbox");
    }
}
