package com.myorg.lsf.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
//nói với Spring Boot tìm các cấu hình bắt đầu bằng lsf.kafka Và bind (gán)
// vào các biến của class này
//Định nghĩa sẵn các giá trị mặc định an toàn cho hệ thống lớn
@ConfigurationProperties(prefix = "lsf.kafka")
public class KafkaProperties {
    private String bootstrapServers;
    private String schemaRegistryUrl;
    private final Producer producer = new Producer();
    private final Consumer consumer = new Consumer();
    private final Observability observability = new Observability();
    private final Dlq dlq = new Dlq();
    @Data
    public static class Producer {
        private String acks = "all";
        private boolean idempotence = true;
        private int retries = 10;
        private int maxInFlight = 5;
        private String compression = "snappy";
        private int lingerMs = 5;
        private int batchSize = 65536;
    }
    @Data
    public static class Consumer {
        private String groupId;
        private String autoOffsetReset = "earliest";
        private boolean batch = true;
        private int concurrency = 10;
        private int maxPollRecords = 1000;
        private final Retry retry = new Retry();
        private String jsonValueType;
    }
    @Data
    public static class Retry{
        private int attempts = 3;
        private Duration backoff = Duration.ofMillis(1);
    }
    @Data
    public static class Dlq{
        private boolean enabled = true;
        private String suffix = ".DLQ";
    }
    @Data
    public static class Observability {
        private boolean observationEnabled = true;
        private boolean warnOnBatch = true;
    }
}



