package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSagaInstanceRepositoryTest {

    @Test
    void shouldPersistAndReloadSagaSnapshots() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            DatabasePopulatorUtils.execute(
                    new ResourceDatabasePopulator(new ClassPathResource("META-INF/spring/lsf/sql/lsf_saga.sql")),
                    database
            );

            LsfSagaProperties properties = new LsfSagaProperties();
            JdbcSagaInstanceRepository repository = new JdbcSagaInstanceRepository(
                    new JdbcTemplate(database),
                    new ObjectMapper(),
                    properties
            );

            SagaInstance instance = new SagaInstance();
            instance.setSagaId("jdbc-saga");
            instance.setDefinitionName("definition");
            instance.setStatus(SagaStatus.RUNNING);
            instance.setPhase(SagaPhase.FORWARD);
            instance.setCurrentStepIndex(0);
            instance.setCurrentStep("step-1");
            instance.setCorrelationId("corr-jdbc");
            instance.setRequestId("req-jdbc");
            instance.setStateData(new ObjectMapper().valueToTree(new JdbcState("ORD-JDBC")));
            instance.setSteps(List.of(SagaInstanceStep.initial("step-1")));
            instance.setCreatedAtMs(100L);
            instance.setUpdatedAtMs(100L);
            instance.setVersion(0L);

            SagaInstance created = repository.create(instance);
            created.setStatus(SagaStatus.WAITING);
            created.getSteps().getFirst().setStatus(SagaStepStatus.DISPATCHED);
            created.setNextTimeoutAtMs(500L);
            created.setUpdatedAtMs(200L);

            SagaInstance saved = repository.save(created);
            SagaInstance loaded = repository.findById("jdbc-saga").orElseThrow();

            assertThat(saved.getVersion()).isEqualTo(1L);
            assertThat(loaded.getStatus()).isEqualTo(SagaStatus.WAITING);
            assertThat(loaded.getRequestId()).isEqualTo("req-jdbc");
            assertThat(loaded.getSteps().getFirst().getStatus()).isEqualTo(SagaStepStatus.DISPATCHED);
            assertThat(repository.findActiveByCorrelationId("corr-jdbc")).isPresent();
            assertThat(repository.findDueTimeouts(600L, 10)).hasSize(1);
        } finally {
            database.shutdown();
        }
    }

    private record JdbcState(String orderId) {
    }
}
