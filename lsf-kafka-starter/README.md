# lsf-kafka-starter

Starter chuẩn hoá Kafka config theo *safe defaults* và cung cấp retry/DLQ + metrics.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Config tối thiểu

```yaml
lsf:
  kafka:
    bootstrap-servers: "localhost:9092"
    consumer:
      group-id: "demo-group"
```

## Những gì starter cung cấp

- `KafkaTemplate`/ProducerFactory với cấu hình an toàn (acks=all, idempotence, retries...)
- Consumer ContainerFactory + `DefaultErrorHandler`
- **Retry + DLQ**: publish sang topic `topic + dlq.suffix` (mặc định `.DLQ`)
- **DLQ headers**: reason, exception class, non-retryable flag, service name, timestamp...
- Metrics: retry/dlq counts (tuỳ theo code hiện tại của bạn)
