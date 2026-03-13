package com.demo.app.flashsale;

import com.myorg.lsf.quota.api.QuotaDecision;
import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.key.QuotaKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleReservationService {

    private static final String DEFAULT_TENANT = "shopA";
    private static final String QUOTA_TYPE = "flashsale_sku";

    private final QuotaReservationFacade quota;
    private final Clock clock = Clock.systemUTC();

    private final Map<String, FlashSaleOrderRecord> ordersById = new ConcurrentHashMap<>();
    private final Map<String, String> orderIdByRequestId = new ConcurrentHashMap<>();

    public FlashSaleOrderResponse reserve(FlashSaleReserveBody body) {
        String tenant = normalizeTenant(body.tenant());
        String sku = requireNonBlank(body.sku(), "sku must not be blank");
        int quantity = body.quantity() == null ? 1 : body.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        String requestId = normalizeRequestId(body.requestId());

        String existingOrderId = orderIdByRequestId.get(requestId);
        if (existingOrderId != null) {
            FlashSaleOrderRecord existing = ordersById.get(existingOrderId);
            if (existing != null) {
                return FlashSaleOrderResponse.from(existing);
            }
        }

        String quotaKey = QuotaKeys.of(tenant, QUOTA_TYPE, sku);
        QuotaResult result = quota.reserve(quotaKey, requestId, quantity);
        FlashSaleOrderStatus status = mapReserveStatus(result.decision());

        Instant now = Instant.now(clock);
        String orderId = UUID.randomUUID().toString();
        FlashSaleOrderRecord record = new FlashSaleOrderRecord(
                orderId,
                tenant,
                sku,
                quotaKey,
                requestId,
                quantity,
                status,
                result.decision(),
                result.used(),
                result.limit(),
                result.remaining(),
                result.holdUntilEpochMs(),
                now,
                now
        );

        ordersById.put(orderId, record);
        orderIdByRequestId.putIfAbsent(requestId, orderId);

        log.info("flashsale.reserve orderId={} sku={} tenant={} requestId={} decision={} used={} limit={} remaining={}",
                orderId, sku, tenant, requestId, result.decision(), result.used(), result.limit(), result.remaining());
        return FlashSaleOrderResponse.from(record);
    }

    public FlashSaleOrderResponse confirm(String orderId) {
        FlashSaleOrderRecord existing = getRequired(orderId);
        QuotaResult result = quota.confirm(existing.quotaKey(), existing.requestId());
        FlashSaleOrderRecord updated = new FlashSaleOrderRecord(
                existing.orderId(),
                existing.tenant(),
                existing.sku(),
                existing.quotaKey(),
                existing.requestId(),
                existing.quantity(),
                mapConfirmStatus(result.decision(), existing.status()),
                result.decision(),
                result.used(),
                result.limit(),
                result.remaining(),
                result.holdUntilEpochMs(),
                existing.createdAt(),
                Instant.now(clock)
        );
        ordersById.put(orderId, updated);
        log.info("flashsale.confirm orderId={} requestId={} decision={} used={}",
                orderId, existing.requestId(), result.decision(), result.used());
        return FlashSaleOrderResponse.from(updated);
    }

    public FlashSaleOrderResponse release(String orderId) {
        FlashSaleOrderRecord existing = getRequired(orderId);
        QuotaResult result = quota.release(existing.quotaKey(), existing.requestId());
        FlashSaleOrderRecord updated = new FlashSaleOrderRecord(
                existing.orderId(),
                existing.tenant(),
                existing.sku(),
                existing.quotaKey(),
                existing.requestId(),
                existing.quantity(),
                mapReleaseStatus(result.decision(), existing.status()),
                result.decision(),
                result.used(),
                result.limit(),
                result.remaining(),
                result.holdUntilEpochMs(),
                existing.createdAt(),
                Instant.now(clock)
        );
        ordersById.put(orderId, updated);
        log.info("flashsale.release orderId={} requestId={} decision={} used={}",
                orderId, existing.requestId(), result.decision(), result.used());
        return FlashSaleOrderResponse.from(updated);
    }

    public FlashSaleOrderResponse get(String orderId) {
        return FlashSaleOrderResponse.from(getRequired(orderId));
    }

    private FlashSaleOrderRecord getRequired(String orderId) {
        FlashSaleOrderRecord record = ordersById.get(orderId);
        if (record == null) {
            throw new IllegalArgumentException("orderId not found: " + orderId);
        }
        return record;
    }

    private static FlashSaleOrderStatus mapReserveStatus(QuotaDecision decision) {
        return switch (decision) {
            case ACCEPTED, DUPLICATE -> FlashSaleOrderStatus.HOLDING;
            case REJECTED, NOT_FOUND -> FlashSaleOrderStatus.REJECTED;
        };
    }

    private static FlashSaleOrderStatus mapConfirmStatus(QuotaDecision decision, FlashSaleOrderStatus current) {
        return switch (decision) {
            case ACCEPTED, DUPLICATE -> FlashSaleOrderStatus.CONFIRMED;
            case NOT_FOUND, REJECTED -> current;
        };
    }

    private static FlashSaleOrderStatus mapReleaseStatus(QuotaDecision decision, FlashSaleOrderStatus current) {
        return switch (decision) {
            case ACCEPTED -> FlashSaleOrderStatus.RELEASED;
            case DUPLICATE, NOT_FOUND, REJECTED -> current;
        };
    }

    private static String normalizeTenant(String tenant) {
        return (tenant == null || tenant.isBlank()) ? DEFAULT_TENANT : tenant;
    }

    private static String normalizeRequestId(String requestId) {
        return (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
