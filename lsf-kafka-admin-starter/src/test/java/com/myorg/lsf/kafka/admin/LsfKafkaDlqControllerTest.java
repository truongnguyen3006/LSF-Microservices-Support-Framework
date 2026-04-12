package com.myorg.lsf.kafka.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LsfKafkaDlqControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LsfKafkaDlqService service;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LsfKafkaDlqController(service))
                .addPlaceholderValue("lsf.kafka.admin.base-path", "/lsf/kafka")
                .build();
    }

    @Test
    void shouldReturnDlqTopics() throws Exception {
        when(service.listDlqTopics()).thenReturn(List.of("orders.DLQ", "payments.DLQ"));

        mockMvc.perform(get("/lsf/kafka/dlq/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("orders.DLQ"))
                .andExpect(jsonPath("$[1]").value("payments.DLQ"));
    }

    @Test
    void shouldTranslateMissingRecordToBadRequest() throws Exception {
        when(service.getRecord("orders.DLQ", 0, 99L))
                .thenThrow(new IllegalArgumentException("No DLQ record found"));

        mockMvc.perform(get("/lsf/kafka/dlq/records/orders.DLQ/0/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("No DLQ record found"));
    }

    @Test
    void shouldTranslateReplayFailureToConflict() throws Exception {
        when(service.replay(any(LsfKafkaReplayRequest.class)))
                .thenThrow(new IllegalStateException("Replay is disabled"));

        mockMvc.perform(post("/lsf/kafka/dlq/replay")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "orders.DLQ",
                                  "partition": 0,
                                  "offset": 15
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value("Replay is disabled"));
    }
}
