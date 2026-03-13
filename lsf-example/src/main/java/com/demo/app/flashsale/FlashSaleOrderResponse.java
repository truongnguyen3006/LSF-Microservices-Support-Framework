package com.demo.app.flashsale;

public record FlashSaleOrderResponse(
        String orderId,
        String tenant,
        String sku,
        String quotaKey,
        String requestId,
        int quantity,
        FlashSaleOrderStatus status,
        String decision,
        int used,
        int limit,
        int remaining,
        long holdUntilEpochMs,
        String createdAt,
        String updatedAt
) {
    public static FlashSaleOrderResponse from(FlashSaleOrderRecord record) {
        return new FlashSaleOrderResponse(
                record.orderId(),
                record.tenant(),
                record.sku(),
                record.quotaKey(),
                record.requestId(),
                record.quantity(),
                record.status(),
                record.decision() == null ? null : record.decision().name(),
                record.used(),
                record.limit(),
                record.remaining(),
                record.holdUntilEpochMs(),
                record.createdAt().toString(),
                record.updatedAt().toString()
        );
    }
}
