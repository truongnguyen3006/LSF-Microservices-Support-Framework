package com.myorg.lsf.outbox.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {OutboxAdminController.class, OutboxAdminControllerTest.TestApplication.class},
        properties = {
                "lsf.outbox.admin.base-path=/lsf/outbox",
                "spring.autoconfigure.exclude=com.myorg.lsf.outbox.mysql.LsfOutboxMySqlAutoConfiguration,com.myorg.lsf.outbox.postgres.LsfOutboxPostgresAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@AutoConfigureMockMvc
class OutboxAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutboxAdminService service;

    @Test
    void shouldReturnNotFoundForMissingEventId() throws Exception {
        when(service.findByEventId("evt-404")).thenReturn(Optional.empty());

        mockMvc.perform(get("/lsf/outbox/event/evt-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.eventId").value("evt-404"));
    }

    @Test
    void shouldTranslateBadRequestForInvalidStatusQuery() throws Exception {
        mockMvc.perform(get("/lsf/outbox")
                        .param("status", "not-a-status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void shouldTranslateForbiddenWhenRetryIsDisabled() throws Exception {
        when(service.requeueFailed(null, true))
                .thenThrow(new IllegalStateException("Retry is disabled. Set lsf.outbox.admin.allow-retry=true to enable."));

        mockMvc.perform(post("/lsf/outbox/requeue/failed")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Retry is disabled. Set lsf.outbox.admin.allow-retry=true to enable."));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
