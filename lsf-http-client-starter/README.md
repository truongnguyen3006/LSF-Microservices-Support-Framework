# lsf-http-client-starter

Starter này cung cấp `RestClient` + service discovery conventions cho synchronous HTTP trong LSF.

## Mục tiêu

- chuẩn hóa cách gọi HTTP nội bộ giữa các service ngoài Kafka
- tái sử dụng `LsfServiceLocator`, `LsfResilienceExecutor` và request/trace conventions đã có
- giảm boilerplate khi team phải tự dựng proxy client, auth và header propagation ở từng service

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-http-client-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Những gì starter đang hỗ trợ

- `@LsfHttpClient` + `@EnableLsfHttpClients`
- build proxy client dựa trên Spring `@HttpExchange`
- resolve endpoint qua `LsfServiceLocator`
- propagate:
  - `correlation-id`
  - `causation-id`
  - `request-id`
  - trace headers như `traceparent`, `tracestate`, `b3`, `x-b3-*`
- internal auth:
  - `API_KEY`
  - `BEARER`
  - `AUTO`
- remote error decode về `LsfErrorResponse`
- retryable/non-retryable classification tương thích với `lsf-resilience-starter`

## Cấu hình cơ bản

```yaml
lsf:
  discovery:
    mode: STATIC
    services:
      inventory-service:
        - host: localhost
          port: 8081
          scheme: http

  http:
    client:
      connect-timeout: 1s
      read-timeout: 2s
      authentication:
        mode: API_KEY
        api-key:
          value: local-dev-key

  resilience:
    instances:
      inventory-http:
        retry:
          enabled: true
          max-attempts: 3
          wait-duration: 200ms
        timeout:
          enabled: true
          duration: 2s
```

## Ví dụ khai báo client

```java
@EnableLsfHttpClients(basePackageClasses = InventoryHttpClient.class)
@Configuration
class SyncClientConfiguration {
}

@LsfHttpClient(
        serviceId = "inventory-service",
        pathPrefix = "/internal",
        resilienceId = "inventory-http",
        authMode = LsfClientAuthMode.API_KEY
)
@HttpExchange
public interface InventoryHttpClient {

    @GetExchange("/health")
    InventoryHealthResponse health();
}
```

## Ghi chú thiết kế

- Module này bám theo Spring HTTP service proxy thay vì tạo một DSL hoàn toàn mới.
- `LsfRemoteServiceException` giữ lại retryability để `LsfResilienceExecutor` biết lúc nào không nên retry nữa.
- Authentication hiện ưu tiên rõ ràng cho internal service-to-service calls, không cố giải quyết mọi mô hình IAM.

## Validation hiện có

- focused tests cho header propagation, trace propagation, auth interceptor và remote error classification
- auto-configuration test cho `@EnableLsfHttpClients`
- cross-module runtime integration test với `lsf-service-web-starter` + `lsf-security-starter` + `lsf-resilience-starter` để verify:
  - gọi thật qua servlet HTTP path
  - API key auth
  - propagate `correlation-id`, `causation-id`, `request-id` và `traceparent`
  - decode `LsfErrorResponse`
  - retry retryable response và dừng ở non-retryable response

## Giới hạn hiện tại

- chưa có reactive HTTP client/runtime tương đương
- chưa có OAuth2 client-credentials flow hoàn chỉnh
- chưa có gRPC convention/runtime đi kèm
