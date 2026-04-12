# lsf-security-starter

Starter này cung cấp security baseline cho service servlet-based trong framework.

## Mục tiêu

- chuẩn hóa authentication/authorization cơ bản giữa các service
- hỗ trợ internal API đơn giản bằng API key
- hỗ trợ JWT resource server cho service cần tích hợp token-based auth

## Những gì đang hỗ trợ

- `lsf.security.mode=API_KEY|JWT`
- `public-paths` và `admin-paths`
- `admin-authorities`
- API key filter cho internal service access
- `JwtDecoder` auto-configuration từ `issuer-uri`, `jwk-set-uri` hoặc `hmac-secret`

## Ví dụ cấu hình API key

```yaml
lsf:
  security:
    enabled: true
    mode: API_KEY
    public-paths:
      - /actuator/health
    admin-paths:
      - /lsf/outbox/**
    api-key:
      header-name: X-API-Key
      value: local-dev-key
      authorities:
        - ROLE_LSF_INTERNAL
```

## Ví dụ cấu hình JWT

```yaml
lsf:
  security:
    enabled: true
    mode: JWT
    jwt:
      issuer-uri: http://localhost:9000/realms/demo
```

## Ghi chú thiết kế

- Starter này hiện là **servlet-first**.
- Path matching dùng `AntPathRequestMatcher` để giảm coupling với MVC internals.
- `API_KEY` phù hợp cho môi trường nội bộ hoặc local integration đơn giản, không nhằm thay thế hoàn toàn IAM.

## Validation hiện có

- servlet integration tests cho `public-paths`, protected paths, admin paths và 401/403 error body
- cross-module runtime integration test với `lsf-service-web-starter` + `lsf-http-client-starter` + `lsf-resilience-starter` để verify API key auth hoạt động trên internal sync HTTP path thật và giữ metadata/error model nhất quán

## Giới hạn hiện tại

- chưa có reactive security support hoàn chỉnh
- chưa có service-to-service token propagation framework
- chưa có RBAC/ABAC abstraction nâng cao
