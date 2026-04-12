package com.myorg.lsf.service.web;

import com.myorg.lsf.contracts.core.context.LsfRequestContext;
import com.myorg.lsf.contracts.core.context.LsfRequestContextHolder;
import com.myorg.lsf.contracts.core.context.LsfTraceContext;
import com.myorg.lsf.contracts.core.context.LsfTraceContextHolder;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.exception.LsfRetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LsfServiceWebIntegrationTest.TestApplication.class,
        properties = {
                "lsf.service.web.enabled=true",
                "spring.application.name=orders-service"
        }
)
@AutoConfigureMockMvc
class LsfServiceWebIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void shouldAcceptLegacyHeadersAndEchoCanonicalHeaders() throws Exception {
        mockMvc.perform(get("/context")
                        .header(CoreHeaders.CORRELATION_ID, "corr-1")
                        .header(CoreHeaders.CAUSATION_ID, "cause-1")
                        .header(CoreHeaders.REQUEST_ID, "req-1")
                        .header(CoreHeaders.TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(header().string(CoreHeaders.HTTP_CORRELATION_ID, "corr-1"))
                .andExpect(header().string(CoreHeaders.HTTP_CAUSATION_ID, "cause-1"))
                .andExpect(header().string(CoreHeaders.HTTP_REQUEST_ID, "req-1"))
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(jsonPath("$.causationId").value("cause-1"))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.traceparent").value("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
    }

    @Test
    void shouldGenerateHeadersAndReturnStandardErrorResponse() throws Exception {
        mockMvc.perform(get("/bad"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CoreHeaders.HTTP_CORRELATION_ID, not(blankOrNullString())))
                .andExpect(header().string(CoreHeaders.HTTP_REQUEST_ID, not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.service").value("orders-service"))
                .andExpect(jsonPath("$.correlationId").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.requestId").value(not(blankOrNullString())));
    }

    @Test
    void shouldMarkRetryableErrors() throws Exception {
        mockMvc.perform(get("/retry"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @SpringBootApplication
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @GetMapping("/context")
        Map<String, String> context() {
            LsfRequestContext context = LsfRequestContextHolder.getContext();
            LsfTraceContext traceContext = LsfTraceContextHolder.getContext();
            Map<String, String> body = new LinkedHashMap<>();
            body.put("correlationId", context == null ? null : context.correlationId());
            body.put("causationId", context == null ? null : context.causationId());
            body.put("requestId", context == null ? null : context.requestId());
            body.put("traceparent", traceContext == null ? null : traceContext.header(CoreHeaders.TRACEPARENT));
            return body;
        }

        @GetMapping("/bad")
        String bad() {
            throw new IllegalArgumentException("bad request");
        }

        @GetMapping("/retry")
        String retry() {
            throw new LsfRetryableException("DOWNSTREAM_TIMEOUT", "downstream timeout");
        }
    }
}
