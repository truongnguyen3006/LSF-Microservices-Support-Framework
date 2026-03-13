package com.demo.app.quota;

public record QuotaActionBody(
        String key,
        String requestId
) {
}
