package com.myorg.lsf.security;

import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ServletApiKeySecurityIntegrationTest.TestApplication.class,
        properties = {
                "lsf.security.enabled=true",
                "lsf.security.mode=api-key",
                "lsf.security.api-key.header-name=X-API-Key",
                "lsf.security.api-key.value=test-key",
                "lsf.security.api-key.authorities[0]=ROLE_LSF_INTERNAL",
                "lsf.security.public-paths[0]=/public/**",
                "lsf.security.admin-paths[0]=/admin/**",
                "lsf.security.admin-authorities[0]=ROLE_LSF_ADMIN"
        }
)
@AutoConfigureMockMvc
class ServletApiKeySecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void shouldPermitPublicPathWithoutApiKey() throws Exception {
        mockMvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void shouldRejectProtectedPathWithoutApiKey() throws Exception {
        mockMvc.perform(get("/secure/ping").header(CoreHeaders.HTTP_CORRELATION_ID, "corr-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CoreHeaders.HTTP_CORRELATION_ID, "corr-1"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId").value("corr-1"));
    }

    @Test
    void shouldAuthenticateProtectedPathWithApiKey() throws Exception {
        mockMvc.perform(get("/secure/ping").header("X-API-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(content().string("secure"));
    }

    @Test
    void shouldDenyAdminPathWithoutAdminAuthority() throws Exception {
        mockMvc.perform(get("/admin/ping").header("X-API-Key", "test-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @SpringBootApplication
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @GetMapping("/public/ping")
        String publicPing() {
            return "public";
        }

        @GetMapping("/secure/ping")
        String securePing() {
            return "secure";
        }

        @GetMapping("/admin/ping")
        String adminPing() {
            return "admin";
        }
    }
}
