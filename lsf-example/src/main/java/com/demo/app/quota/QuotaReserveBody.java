package com.demo.app.quota;

public record QuotaReserveBody(
        String key,
        Integer amount,
        String requestId
) {
}
