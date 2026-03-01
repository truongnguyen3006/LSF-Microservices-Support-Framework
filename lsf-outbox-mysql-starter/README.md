# lsf-outbox-mysql-starter

Outbox pattern cho MySQL: đảm bảo **DB commit** và **publish event** không bị lệch (eventual consistency).

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-mysql-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Enable writer

```yaml
lsf:
  outbox:
    enabled: true
    table: lsf_outbox
```

Gọi API:

```java
@RequiredArgsConstructor
@Service
public class BookingService {
  private final OutboxWriter outbox;

  public void create(...) {
    // trong @Transactional
    outbox.append(envelope, "booking-events", "key-1");
  }
}
```

## Enable publisher poller

```yaml
lsf:
  outbox:
    publisher:
      enabled: true
      scheduling-enabled: true
      batch-size: 50
      claim-strategy: LEASE  # hoặc SKIP_LOCKED (tuỳ DB)
```

## An toàn

`lsf.outbox.table` được validate: chỉ cho phép `table` hoặc `schema.table` (tránh SQL injection ở vị trí identifier).
