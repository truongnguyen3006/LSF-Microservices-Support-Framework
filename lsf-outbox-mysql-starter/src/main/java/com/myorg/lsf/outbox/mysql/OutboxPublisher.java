package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.conventions.CoreHeaders;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.temporal.ChronoUnit;

@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final LsfOutboxMySqlProperties props;
    private final JdbcOutboxRepository repo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final OutboxPublisherHooks hooks;
    private final OutboxMetrics metrics; // may be null if metrics disabled

    private final String instanceId = "outbox-publisher-" + UUID.randomUUID();

    @Scheduled(
            initialDelayString = "#{@lsfOutboxSchedule.initialDelayMs}",
            fixedDelayString = "#{@lsfOutboxSchedule.pollIntervalMs}"
    )
    public void scheduledLoop() {
        if (!props.getPublisher().isEnabled()) return;
        if (!props.getPublisher().isSchedulingEnabled()) return;
        runOnce();
    }


    public void runOnce() {
        if (!props.getPublisher().isEnabled()) return;

        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant leaseUntil = now.plus(props.getPublisher().getLease());

        int claimed = tx.execute(status -> {
            var st = props.getPublisher().getClaimStrategy();
            if (st == LsfOutboxMySqlProperties.Publisher.ClaimStrategy.SKIP_LOCKED) {
                return repo.claimBatchSkipLocked(instanceId, now, leaseUntil, props.getPublisher().getBatchSize());
            }
            return repo.claimBatch(instanceId, now, leaseUntil, props.getPublisher().getBatchSize());
        });
        if (claimed <= 0) return;

        List<OutboxRow> rows = repo.findClaimed(instanceId, now, props.getPublisher().getBatchSize());
        if (rows.isEmpty()) return;

        hooks.afterClaim(rows); // used by IT to simulate crash

        for (OutboxRow row : rows) {
            try {
                hooks.beforeSend(row); // used by IT to simulate publish fail

                EventEnvelope env = mapper.readValue(row.envelopeJson(), EventEnvelope.class);

                // publish sync (MVP)
                ProducerRecord<String, Object> record = new ProducerRecord<>(row.topic(), row.msgKey(), env);
                addHeaders(record, env);
                kafkaTemplate.send(record)
                        .get(props.getPublisher().getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);

                Instant sentAt = clock.instant();
                tx.executeWithoutResult(s -> repo.markSent(row.id(), sentAt));

                if (metrics != null) metrics.incSent();

                log.debug(
                        "OUTBOX SENT -> id={}, eventId={}, topic={}, retryCount={}",
                        row.id(), row.eventId(), row.topic(), row.retryCount()
                );

            } catch (Exception e) {
                if (metrics != null) metrics.incFailed();
                int nextRetryCount = row.retryCount() + 1;
                if (nextRetryCount >= props.getPublisher().getMaxRetries()) {
                    tx.executeWithoutResult(s -> repo.markFailed(row.id(), safeErr(e)));
                    log.error(
                            "OUTBOX FAILED -> id={}, eventId={}, topic={}, retries={}, error={}",
                            row.id(), row.eventId(), row.topic(), nextRetryCount, safeErr(e)
                    );
                    continue;
                }

                Instant nextAttempt = clock.instant().plus(backoff(nextRetryCount));
                if (metrics != null) metrics.incRetry();
                tx.executeWithoutResult(s -> repo.markRetry(row.id(), nextAttempt, safeErr(e)));
                log.warn(
                        "OUTBOX RETRY -> id={}, eventId={}, topic={}, retry={}, nextAttempt={}, error={}",
                        row.id(), row.eventId(), row.topic(), nextRetryCount, nextAttempt, safeErr(e)
                );
            }
        }
    }

    private Duration backoff(int retryCount) {
        Duration base = props.getPublisher().getBackoffBase();
        Duration max = props.getPublisher().getBackoffMax();

        long baseMs = Math.max(1, base.toMillis());

        // retryCount=1 => 2^(0)=1 => backoff = base
        int pow = Math.max(0, retryCount - 1);
        long exp = 1L << Math.min(30, pow);

        long ms = baseMs * exp;
        ms = Math.min(ms, max.toMillis());

        return Duration.ofMillis(ms);
    }

    private String safeErr(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getClass().getSimpleName() + ": " +
                (root.getMessage() == null ? "" : root.getMessage());
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }

    private void addHeaders(ProducerRecord<String, Object> record, EventEnvelope envelope) {
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_ID, bytes(envelope.getEventId())));
        record.headers().add(new RecordHeader(CoreHeaders.EVENT_TYPE, bytes(envelope.getEventType())));
        if (StringUtils.hasText(envelope.getCorrelationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CORRELATION_ID, bytes(envelope.getCorrelationId())));
        }
        if (StringUtils.hasText(envelope.getCausationId())) {
            record.headers().add(new RecordHeader(CoreHeaders.CAUSATION_ID, bytes(envelope.getCausationId())));
        }
        if (StringUtils.hasText(envelope.getRequestId())) {
            record.headers().add(new RecordHeader(CoreHeaders.REQUEST_ID, bytes(envelope.getRequestId())));
        }
        if (envelope.getTraceHeaders() != null) {
            envelope.getTraceHeaders().forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    record.headers().add(new RecordHeader(name, bytes(value)));
                }
            });
        }
    }

    private byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
