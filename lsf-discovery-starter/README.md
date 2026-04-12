# lsf-discovery-starter

Starter này bổ sung service discovery support ở mức framework theo hướng thực dụng: tận dụng discovery runtime có sẵn nếu môi trường đã có, hoặc cho phép static discovery để chạy local/dev/test.

## Mục tiêu

- cung cấp `LsfServiceLocator` như API ổn định cho các module khác của framework
- hỗ trợ static registry local mà không phải dựng discovery server riêng
- fail-fast khi service khai báo discovery là bắt buộc nhưng runtime không đáp ứng

## Những gì starter đang hỗ trợ

- `lsf.discovery.mode=AUTO|STATIC|REQUIRED|DISABLED`
- static service registry qua `lsf.discovery.services.<service-id>[]`
- `DiscoveryClient` và `ReactiveDiscoveryClient` cho chế độ static
- reuse `DiscoveryClient` hiện có nếu môi trường đã cung cấp một implementation khác

## Cấu hình ví dụ

```yaml
lsf:
  discovery:
    mode: STATIC
    services:
      inventory-service:
        - host: localhost
          port: 8081
          scheme: http
      order-service:
        - host: localhost
          port: 8082
          scheme: http
          context-path: /internal
```

## Cách adopter dùng starter

- Dùng `AUTO` khi muốn framework ưu tiên discovery runtime sẵn có, nhưng vẫn có thể fallback static nếu cấu hình local instances.
- Dùng `STATIC` cho local/dev/test hoặc cho hệ nhỏ chưa có registry riêng.
- Dùng `REQUIRED` khi service chỉ hợp lệ nếu có discovery runtime thực sự.
- Dùng `LsfServiceLocator` trong service code hoặc starter khác để resolve `ServiceInstance` hay URI bắt buộc.

## Ghi chú thiết kế

- Static instances hiện dùng các trường `host`, `port`, `secure`, `scheme`, `context-path`, `metadata`.
- Starter này không tự tạo registry server.
- `lsf-http-client-starter` dùng lại `LsfServiceLocator` để build sync HTTP clients.

## Giới hạn hiện tại

- chưa có client-side load balancing strategy riêng ngoài behavior của client sử dụng nó
- chưa có health-aware instance filtering
- chưa có discovery-to-gateway route generation tự động
