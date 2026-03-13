package com.demo.app.flashsale;

import com.myorg.lsf.quota.api.QuotaDecision;

import java.time.Instant;

public record FlashSaleOrderRecord(
        String orderId,
        String tenant,
        String sku,
        String quotaKey,
        String requestId,
        int quantity,
        FlashSaleOrderStatus status,
        QuotaDecision decision,
        int used,
        int limit,
        int remaining,
        long holdUntilEpochMs,
        Instant createdAt,
        Instant updatedAt
) {
}
