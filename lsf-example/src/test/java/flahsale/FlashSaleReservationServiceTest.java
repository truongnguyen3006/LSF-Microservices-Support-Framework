package flahsale;

import com.demo.app.flashsale.FlashSaleOrderResponse;
import com.demo.app.flashsale.FlashSaleOrderStatus;
import com.demo.app.flashsale.FlashSaleReservationService;
import com.demo.app.flashsale.FlashSaleReserveBody;
import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FlashSaleReservationServiceTest {

    private QuotaReservationFacade quota;
    private FlashSaleReservationService service;

    @BeforeEach
    void setUp() {
        quota = mock(QuotaReservationFacade.class);
        service = new FlashSaleReservationService(quota);
    }

    @Test
    void reserveShouldBuildFlashSaleQuotaKeyAndPersistHoldingOrder() {
        when(quota.reserve(anyString(), anyString(), anyInt())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(3)
                .limit(10)
                .holdUntilEpochMs(123456789L)
                .build());

        FlashSaleOrderResponse response = service.reserve(new FlashSaleReserveBody("shopA", "IPHONE15-128-BLACK", 2, "req-1"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(quota).reserve(keyCaptor.capture(), eq("req-1"), eq(2));
        assertThat(keyCaptor.getValue()).isEqualTo("shopA:flashsale_sku:IPHONE15-128-BLACK");
        assertThat(response.status()).isEqualTo(FlashSaleOrderStatus.HOLDING);
        assertThat(response.remaining()).isEqualTo(7);
        assertThat(response.requestId()).isEqualTo("req-1");
    }

    @Test
    void duplicateReserveShouldReturnExistingOrderWithoutCallingQuotaAgain() {
        when(quota.reserve(anyString(), anyString(), anyInt())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(1)
                .limit(5)
                .holdUntilEpochMs(111L)
                .build());

        FlashSaleOrderResponse first = service.reserve(new FlashSaleReserveBody("shopA", "SKU-1", 1, "req-dup"));
        FlashSaleOrderResponse second = service.reserve(new FlashSaleReserveBody("shopA", "SKU-1", 1, "req-dup"));

        verify(quota, times(1)).reserve(anyString(), anyString(), anyInt());
        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo(FlashSaleOrderStatus.HOLDING);
    }

    @Test
    void confirmShouldMoveOrderToConfirmed() {
        when(quota.reserve(anyString(), anyString(), anyInt())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(1)
                .limit(5)
                .holdUntilEpochMs(111L)
                .build());
        when(quota.confirm(anyString(), anyString())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(1)
                .limit(0)
                .holdUntilEpochMs(0)
                .build());

        FlashSaleOrderResponse reserved = service.reserve(new FlashSaleReserveBody("shopA", "SKU-2", 1, "req-ok"));
        FlashSaleOrderResponse confirmed = service.confirm(reserved.orderId());

        assertThat(confirmed.status()).isEqualTo(FlashSaleOrderStatus.CONFIRMED);
        verify(quota).confirm("shopA:flashsale_sku:SKU-2", "req-ok");
    }

    @Test
    void releaseShouldMoveOrderToReleased() {
        when(quota.reserve(anyString(), anyString(), anyInt())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(1)
                .limit(5)
                .holdUntilEpochMs(111L)
                .build());
        when(quota.release(anyString(), anyString())).thenReturn(QuotaResult.builder()
                .decision(QuotaDecision.ACCEPTED)
                .used(0)
                .limit(0)
                .holdUntilEpochMs(0)
                .build());

        FlashSaleOrderResponse reserved = service.reserve(new FlashSaleReserveBody("shopA", "SKU-3", 1, "req-rel"));
        FlashSaleOrderResponse released = service.release(reserved.orderId());

        assertThat(released.status()).isEqualTo(FlashSaleOrderStatus.RELEASED);
        verify(quota).release("shopA:flashsale_sku:SKU-3", "req-rel");
    }

    @Test
    void missingOrderShouldFailFast() {
        assertThatThrownBy(() -> service.confirm("missing-order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId not found");
    }
}
