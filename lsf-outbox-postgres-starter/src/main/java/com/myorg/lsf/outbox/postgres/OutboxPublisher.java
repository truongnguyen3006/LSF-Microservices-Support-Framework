package com.myorg.lsf.outbox.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
@ConditionalOnBean({ KafkaTemplate.class, JdbcOutboxRepository.class })
public class OutboxPublisher {

    private final LsfOutboxPostgresProperties props;
    private final JdbcOutboxRepository repo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final OutboxPublisherHooks hooks;
    private final OutboxMetrics metrics; // may be null

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
        int claimed = tx.execute(st -> {
            var cs = props.getPublisher().getClaimStrategy();
            if (cs == LsfOutboxPostgresProperties.Publisher.ClaimStrategy.SKIP_LOCKED) {
                return repo.claimBatchSkipLocked(instanceId, now, leaseUntil, props.getPublisher().getBatchSize());
            }
            return repo.claimBatch(instanceId, now, leaseUntil, props.getPublisher().getBatchSize());
        });
        if (claimed <= 0) return;

        List<OutboxRow> rows = repo.findClaimed(instanceId, now, props.getPublisher().getBatchSize());
        if (rows.isEmpty()) return;

        hooks.afterClaim(rows);

        for (OutboxRow row : rows) {
            try {
                hooks.beforeSend(row);

                EventEnvelope env = mapper.readValue(row.envelopeJson(), EventEnvelope.class);

                kafkaTemplate.send(row.topic(), row.msgKey(), env)
                        .get(props.getPublisher().getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);

                Instant sentAt = clock.instant();
                tx.executeWithoutResult(st -> repo.markSent(row.id(), sentAt));
                if (metrics != null) metrics.incSent();

                log.debug(
                        "OUTBOX SENT -> id={}, eventId={}, topic={}, retryCount={}",
                        row.id(), row.eventId(), row.topic(), row.retryCount()
                );

            } catch (Exception e) {
                if (metrics != null) metrics.incFailed();

                int nextRetryCount = row.retryCount() + 1;
                if (nextRetryCount >= props.getPublisher().getMaxRetries()) {
                    tx.executeWithoutResult(st -> repo.markFailed(row.id(), safeErr(e)));
                    log.error(
                            "OUTBOX FAILED -> id={}, eventId={}, topic={}, retries={}, error={}",
                            row.id(), row.eventId(), row.topic(), nextRetryCount, safeErr(e)
                    );
                    continue;
                }

                Instant nextAttempt = clock.instant().plus(backoff(nextRetryCount));
                if (metrics != null) metrics.incRetry();
                tx.executeWithoutResult(st -> repo.markRetry(row.id(), nextAttempt, safeErr(e)));
                log.warn(
                        "OUTBOX RETRY -> id={}, eventId={}, topic={}, retry={}, nextAttempt={}, error={}",
                        row.id(), row.eventId(), row.topic(), nextRetryCount, nextAttempt, safeErr(e)
                );
            }
        }
    }

    /** retryCount=1 => base, retryCount=2 => 2*base, ... */
    private Duration backoff(int retryCount) {
        Duration base = props.getPublisher().getBackoffBase();
        Duration max = props.getPublisher().getBackoffMax();

        long baseMs = Math.max(1, base.toMillis());
        int pow = Math.max(0, retryCount - 1);
        long exp = 1L << Math.min(30, pow);

        long ms = baseMs * exp;
        ms = Math.min(ms, max.toMillis());
        return Duration.ofMillis(ms);
    }

    private String safeErr(Throwable e) {
        String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }
}