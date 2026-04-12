# lsf-eventing-starter

Starter này cho phép service xử lý `EventEnvelope` theo mô hình handler thay vì phải tự viết `@KafkaListener` và tự route từng event type.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-eventing-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Những gì starter cung cấp

- `@LsfEventHandler` để khai báo handler theo `eventType`
- `HandlerRegistry` và `HandlerMethodInvoker`
- `LsfEnvelopeListener` để nhận `EventEnvelope` từ Kafka
- `LsfDispatcher` abstraction với các decorator cho context/idempotency/observability
- `LsfPublisher` để publish event mà không phải tự bọc envelope thủ công
- idempotency store:
  - in-memory
  - Redis
  - auto chọn Redis hoặc memory

## Handler example

```java
@Component
public class BookingHandlers {

    @LsfEventHandler(value = "booking.created.v1", payload = BookingCreated.class)
    public void onCreated(EventEnvelope envelope, BookingCreated payload) {
        // business logic
    }
}
```

Handler method hiện hỗ trợ 2 kiểu chữ ký:

- `(Payload payload)`
- `(EventEnvelope envelope, Payload payload)`

## Cấu hình listener và idempotency

```yaml
lsf:
  eventing:
    producer-name: order-service
    consume-topics:
      - booking-events
    ignore-unknown-event-type: false
    listener:
      enabled: true
    idempotency:
      enabled: true
      store: auto
      ttl: 24h
      processing-ttl: 5m
      cleanup-interval: 5m
      max-entries: 500000
      key-prefix: lsf:idemp:
      require-redis: false
```

## Publish event

```java
publisher.publish(
        "booking-events",
        "booking-001",
        "booking.created.v1",
        "booking-001",
        new BookingCreated(...)
);
```

Nếu cần tự cấp metadata rõ hơn:

```java
publisher.publish(
        "booking-events",
        "booking-001",
        "booking.created.v1",
        "booking-001",
        new BookingCreated(...),
        LsfPublishOptions.builder()
                .correlationId("corr-123")
                .causationId("evt-001")
                .requestId("req-123")
                .producer("booking-service")
                .build()
);
```

## Ghi chú thiết kế

- `DefaultLsfPublisher` tự tạo `EventEnvelope`, bổ sung headers chuẩn và enrich trace headers khi có context.
- `ignoreUnknownEventType=true` giúp service chọn log+skip thay vì fail nếu gặp event type chưa đăng ký handler.
- `store=auto` ưu tiên Redis khi có `StringRedisTemplate`, nếu không sẽ fallback memory.

## Validation hiện có

- có cross-module runtime test ghép `LsfPublisher`, Kafka listener/dispatcher path và `lsf-observability-starter`
- suite này verify publish -> consume -> dispatch, propagation của `correlationId` / `causationId` / `requestId`, cùng metrics và observation trên dispatcher path

## Giới hạn hiện tại

- starter này giải quyết event dispatch ở mức framework, không phải generic workflow engine
- chưa mở rộng thành bộ cross-module runtime lớn cho mọi transport/runtime combination trong repo
