# LSF (Large Scale Framework)

LSF là bộ **starter/framework** cho các hệ thống **microservices large-scale** (đăng ký học phần, đặt vé, TMĐT, ...),
nhằm gom các *cross-cutting concerns* (Kafka bootstrap, retry/DLQ, idempotency/dedup, outbox, observability...) để dev chỉ tập trung viết logic nghiệp vụ.

## Modules chính

- **lsf-contracts**: `EventEnvelope`, headers, conventions
- **lsf-kafka-starter**: Kafka producer/consumer *safe defaults*, retry/backoff, DLQ + DLQ headers, metrics
- **lsf-eventing-starter**: annotation-based handler dispatch (`@LsfEventHandler`), publisher `LsfPublisher`, (optional) idempotency store (memory/redis)
- **lsf-observability-starter**: MDC + metrics cho dispatch outcome/latency
- **lsf-outbox-core**: `OutboxWriter` API
- **lsf-outbox-mysql-starter / lsf-outbox-postgres-starter**: Outbox pattern (writer + publisher poller + lease/skip-locked) + metrics
- **lsf-outbox-admin-starter**: REST admin để list/requeue/mark-failed/delete (delete mặc định bị chặn)
- **lsf-example**: demo app + integration tests (Testcontainers)

## Chạy demo

```bash
# ở thư mục lsf-parent
mvn -q -DskipTests package

# chạy demo
mvn -pl lsf-example spring-boot:run
```

> Lưu ý: demo/integration tests dùng Testcontainers nên cần Docker.
