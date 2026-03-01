package com.myorg.lsf.outbox.mysql;

import com.myorg.lsf.outbox.sql.OutboxSql;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@Data
@ConfigurationProperties(prefix = "lsf.outbox")
public class LsfOutboxMySqlProperties {
    //Enable Outbox writer beans (insert rows).
    private boolean enabled = false;
    private String table = "lsf_outbox";
    private ZoneId zone = ZoneId.of("UTC");
    private Publisher publisher = new Publisher();
    private Metrics metrics = new Metrics();

    @Data
    public static class Publisher {
        //Enable publisher poller (publish NEW/RETRY rows to Kafka).
        private boolean enabled = false;
        //If false, scheduled loop won't run; can still call runOnce() manually (tests).
        private boolean schedulingEnabled = true;
        //Fixed delay between polls.
        private Duration pollInterval = Duration.ofSeconds(1);
        private Duration initialDelay = Duration.ofSeconds(1);
        //Number of rows per batch
        private int batchSize = 50;
        private Duration lease = Duration.ofSeconds(10);
        private Duration backoffBase = Duration.ofSeconds(1);
        private Duration backoffMax = Duration.ofSeconds(60);
        //Max retry attempts before fail
        private int maxRetries = 10;
        //Kafka send
        private Duration sendTimeout = Duration.ofSeconds(10);

        // inside LsfOutboxMySqlProperties.Publisher
        public enum ClaimStrategy {
            LEASE,
            SKIP_LOCKED
        }

        private ClaimStrategy claimStrategy = ClaimStrategy.LEASE;

    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
    }

    @PostConstruct
    public void validate() {
        this.table = OutboxSql.validateTableName(this.table);
    }

}
