# lsf-service-web-starter

Starter này cung cấp servlet conventions cho synchronous HTTP trong Phase 3.

## Mục tiêu

- chuẩn hóa request context cho `correlation-id`, `causation-id`, `request-id`
- chuẩn hóa error response cho sync APIs giữa các service
- giảm việc mỗi service tự viết filter, controller advice, và security error body riêng

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-service-web-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Những gì đang hỗ trợ

- filter cho servlet requests:
  - đọc canonical headers `correlation-id`, `causation-id`, `request-id`
  - chấp nhận legacy aliases `lsf-*` và `X-Correlation-Id`
  - tự generate `correlation-id` / `request-id` nếu cần
- request context chung qua `LsfRequestContext` và `LsfRequestContextHolder`
- response header echo cho canonical headers
- `LsfErrorResponse` cho:
  - validation / bad request
  - `LsfRetryableException` / `LsfNonRetryableException`
  - timeout / circuit open / rate limit
  - generic 5xx fallback
- 401/403 từ `lsf-security-starter` cũng có thể dùng cùng error model

## Cấu hình cơ bản

```yaml
lsf:
  service:
    web:
      enabled: true
      generate-correlation-id: true
      generate-request-id: true
      echo-headers: true
```

## Ví dụ error response

```json
{
  "timestamp": "2026-04-06T14:59:27.666829Z",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "message": "Request is invalid",
  "path": "/internal/orders",
  "retryable": false,
  "service": "order-service",
  "correlationId": "corr-123",
  "causationId": "evt-456",
  "requestId": "req-789",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

## Ghi chú thiết kế

- Module này là **servlet-first**, không cố tự thay thế Spring MVC error handling.
- Header convention giữ canonical names cho sync HTTP, nhưng vẫn nhận aliases cũ để tương thích ngược.
- `LsfErrorResponse` được đặt ở `lsf-contracts` để client/server dùng chung cùng JSON shape.

## Validation hiện có

- MockMvc integration tests cho canonical/legacy headers, generated IDs và `LsfErrorResponse`
- cross-module runtime integration test với `lsf-http-client-starter` + `lsf-security-starter` + `lsf-resilience-starter` để verify:
  - request context đi qua HTTP client thật
  - security error model và business error model dùng chung `LsfErrorResponse`
  - retryable/non-retryable semantics giữ nguyên khi downstream gọi qua `RestClient` proxy

## Giới hạn hiện tại

- chưa có reactive/WebFlux runtime tương đương
- chưa bridge sang gRPC metadata conventions
- chưa có field-level validation payload phong phú kiểu full `ProblemDetail` extension catalog
