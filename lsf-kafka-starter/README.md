# lsf-kafka-starter

Starter này chuẩn hóa Kafka producer/consumer defaults cho LSF và cung cấp retry/DLQ baseline ở mức framework.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Cấu hình tối thiểu

```yaml
lsf:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: demo-group
```

## Những gì starter cung cấp

- `KafkaTemplate`, producer factory và consumer factory với safe defaults
- producer defaults như:
  - `acks=all`
  - `idempotence=true`
  - retries, compression, linger, batch size
- consumer defaults như:
  - `auto-offset-reset`
  - `max-poll-records`
  - retry attempts và backoff
  - concurrency
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`
- DLQ support với suffix mặc định `.DLQ`
- DLQ headers cho reason, exception class/message, original topic/partition/offset, service name và timestamp
- metrics pre-register cho:
  - `lsf.kafka.retry`
  - `lsf.kafka.dlq`
  - `lsf.kafka.recovery_failed`

## Cấu hình ví dụ

```yaml
lsf:
  kafka:
    bootstrap-servers: localhost:9092
    schema-registry-url: http://localhost:8081
    consumer:
      group-id: order-service
      batch: true
      concurrency: 4
      max-poll-records: 500
      retry:
        attempts: 3
        backoff: 200ms
    dlq:
      enabled: true
      suffix: .DLQ
    observability:
      observation-enabled: true
      warn-on-batch: true
```

## Ghi chú thiết kế

- Starter này cung cấp baseline Kafka runtime; business topics, payloads và handler logic vẫn thuộc về service adopter.
- `LsfNonRetryableException`, deserialization errors và serialization errors được đánh dấu là không retry.
- `LsfDlqReasonClassifier` cho phép phân loại lý do vào DLQ theo convention của framework.

## Giới hạn hiện tại

- repo chưa có full integration suite với broker thật cho toàn bộ flow retry -> DLQ -> replay
- phần schema governance vẫn dựa vào hạ tầng Kafka/Schema Registry bên ngoài, không được framework trừu tượng hóa hoàn toàn
