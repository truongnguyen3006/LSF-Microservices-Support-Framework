# lsf-config-starter

Starter này chuẩn hóa cách bật centralized configuration cho service dùng LSF.

## Mục tiêu

- gom các mode cấu hình tập trung phổ biến vào một convention chung
- thiết lập `spring.config.import` đủ sớm trong vòng đời Spring Boot
- giữ đường dùng local/container-friendly thay vì buộc mọi adopter phải có config server riêng

## Những gì starter đang hỗ trợ

- `lsf.config.mode=NONE|FILE|CONFIGTREE|CONFIG_SERVER`
- bootstrap import qua `EnvironmentPostProcessor`
- map sang `spring.cloud.config.*` khi dùng `CONFIG_SERVER`
- validation sớm cho các trường hợp cần `spring.application.name`

## Cấu hình ví dụ

### Dùng local file/config tree

```properties
lsf.config.enabled=true
lsf.config.mode=CONFIGTREE
lsf.config.import-location=./config/
lsf.config.optional=true
```

### Dùng Spring Cloud Config Server

```properties
lsf.config.enabled=true
lsf.config.mode=CONFIG_SERVER
lsf.config.config-server.uri=http://localhost:8888
lsf.config.config-server.fail-fast=true
spring.application.name=inventory-service
```

## Ghi chú thiết kế

- Vì `spring.config.import` được xử lý rất sớm, các khóa `lsf.config.*` nên được cấp qua environment variables, system properties, command line hoặc bootstrap properties có sẵn từ đầu tiến trình.
- `FILE` và `CONFIGTREE` phù hợp hơn cho local/dev/container runtime đơn giản.
- Starter này tận dụng Spring Cloud Config khi cần, không tự re-implement config server.

## Giới hạn hiện tại

- chưa có refresh/reload abstraction riêng của framework
- chưa có encryption/secret rotation abstraction
- chưa có governance layer cho config versioning
