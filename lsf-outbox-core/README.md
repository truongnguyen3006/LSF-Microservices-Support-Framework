# lsf-outbox-core

Module này cung cấp abstraction cốt lõi cho outbox trong LSF, tách khỏi chi tiết của MySQL hay PostgreSQL.

## Vai trò trong framework

`lsf-outbox-core` hiện giữ hai phần chính:

- `OutboxWriter`: API chung để append `EventEnvelope` vào outbox trong cùng transaction với business data
- `OutboxSql`: helper validate table identifier an toàn trước khi các runtime module ghép tên bảng vào SQL

## Vì sao phần này thuộc framework

- Outbox là concern cross-cutting, nhưng implementation chi tiết lại phụ thuộc database.
- Tách core abstraction ra module riêng giúp service code không phải gắn chặt với MySQL hay PostgreSQL runtime.
- Các runtime như `lsf-outbox-mysql-starter` và `lsf-outbox-postgres-starter` có thể cùng dựa vào một interface chung.

## Cách service developer dùng module này

Service thường không dùng `lsf-outbox-core` một mình. Cách dùng chuẩn là:

1. phụ thuộc `lsf-outbox-core`
2. chọn thêm một runtime starter theo database
3. inject `OutboxWriter` trong service code

```java
@Transactional
public void publishAfterUpdate(EventEnvelope envelope, String topic, String key) {
    // update domain data
    outboxWriter.append(envelope, topic, key);
}
```

## Giới hạn hiện tại

- Module này không tự tạo scheduler, repository hay publisher runtime.
- Nó không phải complete outbox solution nếu đứng một mình; cần một runtime starter theo database.
- Việc migration schema và ownership của bảng outbox vẫn là quyết định ở tầng adopter.
