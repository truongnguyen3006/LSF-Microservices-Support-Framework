# lsf-kafka-admin-starter

Starter này cung cấp tooling nội bộ cho Kafka DLQ: liệt kê topic DLQ, inspect record, xem metadata gốc và replay có kiểm soát.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-admin-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Cấu hình bật starter

```yaml
lsf:
  kafka:
    admin:
      enabled: true
      base-path: /lsf/kafka
      default-limit: 25
      max-limit: 200
      allow-replay: true
      dlq-suffix: .DLQ
      poll-timeout: 2s
```

## Endpoints hiện có

- `GET /lsf/kafka/dlq/topics`
- `GET /lsf/kafka/dlq/records?topic=orders.DLQ&limit=20`
- `GET /lsf/kafka/dlq/records?topic=orders.DLQ&partition=0&beforeOffset=200`
- `GET /lsf/kafka/dlq/records/orders.DLQ/0/15`
- `POST /lsf/kafka/dlq/replay`

## Replay request example

```json
{
  "topic": "orders.DLQ",
  "partition": 0,
  "offset": 15,
  "targetTopic": "orders",
  "retainDlqHeaders": false
}
```

## Ghi chú thiết kế

- Starter này tận dụng `ConsumerFactory`, `KafkaTemplate` và `AdminClient` hiện có trong application context.
- Record replay sẽ gắn thêm các header:
  - `lsf.replay.source.topic`
  - `lsf.replay.source.partition`
  - `lsf.replay.source.offset`
  - `lsf.replay.replayed_at`
- Replay có metric riêng qua `LsfKafkaReplayMetrics`.
- `allow-replay` nên chỉ bật ở internal environment hoặc admin service riêng.

## Validation hiện có

- broker-backed Testcontainers regression để verify đọc DLQ records trên broker thật
- verify single-record replay với source replay headers được gắn lại đúng
- verify replay metrics cho đường thành công

## Giới hạn hiện tại

- Starter này không tự cung cấp auth/audit policy; adopter phải bảo vệ endpoint ở tầng security/network.
- Luồng replay hiện tập trung vào single-record replay, chưa phải bulk remediation workflow.
- Repo đã có broker-backed regression cho các path cốt lõi, nhưng chưa phải end-to-end remediation workflow đầy đủ cho mọi tình huống vận hành.
- Auto-configuration tests vẫn có thể sinh warning khi không có local broker ở `localhost:9092`.
