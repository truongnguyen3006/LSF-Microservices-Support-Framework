package it;

import com.demo.app.DemoQuotaController;
import com.demo.app.quota.QuotaExceptionHandler;
import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaPolicyNotFoundException;
import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.api.QuotaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoQuotaControllerWebTest {

    @Mock
    private QuotaReservationFacade facade;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DemoQuotaController(facade))
                .setControllerAdvice(new QuotaExceptionHandler())
                .build();
    }

    @Test
    void reserveJsonShouldReturnQuotaResult() throws Exception {
        when(facade.reserve(anyString(), anyString(), anyInt()))
                .thenReturn(QuotaResult.builder()
                        .decision(QuotaDecision.ACCEPTED)
                        .state(QuotaState.RESERVED)
                        .used(1)
                        .limit(10)
                        .holdUntilEpochMs(1_700_000_000_000L)
                        .build());

        mvc.perform(post("/quota/reserve-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"key\": \"flashsale:sku-01\",
                                  \"amount\": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.state").value("RESERVED"))
                .andExpect(jsonPath("$.used").value(1))
                .andExpect(jsonPath("$.limit").value(10));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> amountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(facade).reserve(keyCaptor.capture(), requestIdCaptor.capture(), amountCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("flashsale:sku-01");
        assertThat(amountCaptor.getValue()).isEqualTo(1);
        assertThat(requestIdCaptor.getValue()).isNotBlank();
    }

    @Test
    void reserveJsonShouldMapIllegalArgumentTo400() throws Exception {
        when(facade.reserve(anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("amount must be > 0"));

        mvc.perform(post("/quota/reserve-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"key\": \"flashsale:sku-01\",
                                  \"amount\": 0,
                                  \"requestId\": \"REQ-001\"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("amount must be > 0"))
                .andExpect(jsonPath("$.path").value("/quota/reserve-json"));
    }

    @Test
    void reserveJsonShouldMapMissingPolicyTo404() throws Exception {
        when(facade.reserve(anyString(), anyString(), anyInt()))
                .thenThrow(new QuotaPolicyNotFoundException("flashsale:sku-unknown"));

        mvc.perform(post("/quota/reserve-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"key\": \"flashsale:sku-unknown\",
                                  \"amount\": 1,
                                  \"requestId\": \"REQ-404\"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("QUOTA_POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/quota/reserve-json"));
    }
}
