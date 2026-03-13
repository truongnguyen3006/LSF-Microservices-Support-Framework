package com.demo.app.flashsale;

public record FlashSaleReserveBody(
        String tenant,
        String sku,
        Integer quantity,
        String requestId
) {
}
