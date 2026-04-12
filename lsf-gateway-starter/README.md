# lsf-gateway-starter

Starter này cung cấp gateway conventions tối thiểu trên nền Spring Cloud Gateway.

## Mục tiêu

- chuẩn hóa route declaration ở mức framework
- thêm correlation id propagation mặc định
- tránh biến Phase 1 thành một gateway product riêng

## Những gì đang hỗ trợ

- route config qua `lsf.gateway.routes[*]`
- fields chính: `id`, `path`, `uri`, `methods`, `strip-prefix`
- add request/response headers theo route
- `X-Correlation-Id` filter toàn cục

## Ví dụ cấu hình

```yaml
lsf:
  gateway:
    enabled: true
    routes:
      - id: inventory-api
        path: /api/inventory/**
        uri: http://localhost:8081
        methods: [GET, POST]
        strip-prefix: 1
```

## Ghi chú thiết kế

- Module này dựa trên Spring Cloud Gateway, không tự viết gateway runtime riêng.
- Correlation id được giữ đơn giản để dễ debug local và liên kết log giữa gateway với downstream services.
- Route API cố ý nhỏ, chỉ bao phủ phần thiết thực cho Phase 1.

## Giới hạn hiện tại

- chưa có gateway security hardening riêng
- chưa có rate-limit/filter chain theo route ở mức gateway
- chưa có integration discovery-to-gateway routes tự động
