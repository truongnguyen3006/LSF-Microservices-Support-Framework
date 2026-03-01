package com.myorg.lsf.outbox.admin;

import java.time.Instant;

public record OutboxAdminRow(
        long id,
        String topic,
        String msgKey,
        String eventId,
        String eventType,
        OutboxStatus status,
        int retryCount,
        Instant createdAt,
        Instant sentAt,
        Instant nextAttemptAt,
        Instant leaseUntil,
        String leaseOwner,
        String lastError
) {}