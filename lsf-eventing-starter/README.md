# lsf-eventing-starter

Dev chỉ cần viết handler nghiệp vụ bằng annotation. Framework lo việc đọc `EventEnvelope`,
convert payload, dispatch đúng handler, và (optional) idempotency/dedup.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-eventing-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Handler

```java
@Component
public class BookingHandlers {

  @LsfEventHandler(value = "booking.created", payload = BookingCreated.class)
  public void onCreated(BookingCreated evt) {
    // business logic
  }
}
```

## Listener topics

```yaml
lsf:
  eventing:
    consume-topics:
      - booking-events
```

## Publish event

```java
@RequiredArgsConstructor
@Service
public class BookingService {
  private final LsfPublisher publisher;

  public void create(...) {
    publisher.publish("booking-events", "key-1", "booking.created", "B001", new BookingCreated(...));
  }
}
```

## Idempotency (dedup)

```yaml
lsf:
  eventing:
    idempotency:
      enabled: true
      store: auto   # auto | redis | memory
```

- `auto`: ưu tiên Redis nếu có; fallback memory
- `redis`: bắt buộc dùng Redis (thiếu Redis dependency -> fail fast)
- `memory`: luôn dùng InMemory (phù hợp dev/test)

> Tip: keyPrefix hỗ trợ `{groupId}` để phân vùng theo consumer group.
