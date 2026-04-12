# lsf-resilience-starter

Starter này gom các policy resilience nền tảng trên Resilience4j.

## Mục tiêu

- cung cấp một baseline chung cho circuit breaker, retry, timeout và rate limit
- giữ API đủ nhỏ để tái sử dụng ở nhiều loại integration khác nhau
- tránh khóa framework vào một HTTP client cụ thể ở Phase 1

## Những gì đang hỗ trợ

- `circuit-breaker`
- `retry`
- `timeout`
- `rate-limit`
- cấu hình theo từng instance qua `lsf.resilience.instances.<name>.*`
- execution API qua `LsfResilienceExecutor`

## Ví dụ cấu hình

```yaml
lsf:
  resilience:
    instances:
      inventory-call:
        circuit-breaker:
          enabled: true
          sliding-window-size: 20
        retry:
          enabled: true
          max-attempts: 3
          wait-duration: 200ms
        timeout:
          enabled: true
          duration: 2s
        rate-limit:
          enabled: true
          limit-for-period: 50
          limit-refresh-period: 1s
```

## Ví dụ sử dụng

```java
String value = resilienceExecutor.execute("inventory-call", () -> downstreamCall());
```

## Ghi chú thiết kế

- Đây là reusable execution layer; Phase 3 gắn nó vào `lsf-http-client-starter` thay vì nhúng policy logic trực tiếp vào client code.
- Policy resolver hỗ trợ defaults và override theo instance.
- Module này được thiết kế để Phase sau có thể gắn vào HTTP client/gRPC client mà không phải thay core policy model.

## Validation hiện có

- focused tests cho retry, circuit breaker, timeout, rate limit và non-retryable classification
- cross-module runtime integration test với `lsf-http-client-starter` + `lsf-service-web-starter` + `lsf-security-starter` để verify retry chỉ lặp lại khi downstream trả lỗi `retryable=true` và dừng ngay với non-retryable response

## Giới hạn hiện tại

- classification hiện tại dựa trên `LsfRetryAware` và mapping của integration layer, chưa phải một policy DSL quá chi tiết
- chưa có observability bridge sâu cho từng policy event
