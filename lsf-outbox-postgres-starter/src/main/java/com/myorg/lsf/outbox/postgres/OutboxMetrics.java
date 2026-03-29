package com.myorg.lsf.outbox.postgres;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final JdbcOutboxRepository repo;
    private final Clock clock;
    private final String service;
    private final String table;

    private Counter append;
    private Counter sent;
    private Counter retry;
    private Counter failed;

    // alias cũ để không làm gãy dashboard/demo cũ
    private Counter publishedAlias;
    private Counter failedAlias;

    public void preRegister() {
        Tags tags = Tags.of("service", service, "table", table);

        append = Counter.builder("lsf.outbox.append").tags(tags).register(registry);
        sent = Counter.builder("lsf.outbox.sent").tags(tags).register(registry);
        retry = Counter.builder("lsf.outbox.retry").tags(tags).register(registry);
        failed = Counter.builder("lsf.outbox.fail").tags(tags).register(registry);

        publishedAlias = Counter.builder("outbox.published").tags(tags).register(registry);
        failedAlias = Counter.builder("outbox.failed").tags(tags).register(registry);

        registry.gauge("lsf.outbox.pending", tags, repo, r -> r.countPending(clock.instant()));
        registry.gauge("outbox.pending", tags, repo, r -> r.countPending(clock.instant()));
    }

    public void incAppend() {
        if (append != null) append.increment();
    }

    public void incSent() {
        if (sent != null) sent.increment();
        if (publishedAlias != null) publishedAlias.increment();
    }

    public void incRetry() {
        if (retry != null) retry.increment();
    }

    public void incFailed() {
        if (failed != null) failed.increment();
        if (failedAlias != null) failedAlias.increment();
    }
}