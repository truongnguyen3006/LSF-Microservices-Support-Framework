# lsf-contracts

Module này chứa các hợp đồng dùng chung cho nhiều starter và nhiều service trong hệ LSF.

## Vai trò trong framework

`lsf-contracts` là lớp nền để các module khác cùng nói chung một “ngôn ngữ”:

- `EventEnvelope` cho async event payload + metadata
- `EnvelopeBuilder` để tạo envelope theo cùng convention
- `CoreHeaders` cho canonical header names
- `LsfRequestContext`, `LsfRequestContextHolder`, `LsfTraceContext`, `LsfTraceContextHolder`
- `LsfRetryableException`, `LsfNonRetryableException`, `LsfRetryAware`, `LsfRetryDecisions`
- `LsfErrorResponse` cho sync HTTP error model
- quota command/result contracts trong package `contracts.quota`

## Vì sao phần này thuộc framework

- Shared metadata và error model không nên bị định nghĩa khác nhau ở từng service.
- Nhiều starter của repo cần dùng chung envelope, request context và retry classification để giữ hành vi nhất quán.
- Tách contracts ra module riêng giúp adopter chỉ phụ thuộc vào phần model chung khi chưa cần toàn bộ runtime starter.

## Cách service developer dùng module này

### 1. Dùng `EventEnvelope` cho event-driven integration

```java
EventEnvelope envelope = EnvelopeBuilder.wrap(
        objectMapper,
        "inventory.reserved.v1",
        1,
        reservationId,
        correlationId,
        causationId,
        requestId,
        "inventory-service",
        payload
);
```

### 2. Dùng `LsfErrorResponse` cho sync APIs

`lsf-service-web-starter` và `lsf-http-client-starter` dùng record này để server và client cùng hiểu một JSON shape chung cho lỗi.

### 3. Dùng retry classification chung

`lsf-resilience-starter`, `lsf-kafka-starter` và sync HTTP path đều có thể dựa vào `LsfRetryAware` hoặc các exception retryable/non-retryable để quyết định có retry hay không.

## Giới hạn hiện tại

- Module này không phải schema registry abstraction hay compatibility governance tool.
- Nó không tự giải quyết version negotiation giữa các service.
- Việc evolve contract giữa các team vẫn cần governance ở tầng adopter hoặc tổ chức sử dụng framework.
