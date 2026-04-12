# lsf-example

`lsf-example` là application minh họa cách nhiều module LSF phối hợp với nhau trong một service đơn giản. Module này không nhằm thay thế consumer project thật như `ecommerce-backend`, nhưng rất hữu ích khi cần demo nhanh các capability cốt lõi của framework mà không phải khởi động cả hệ nhiều service.

## Module này đang chứng minh điều gì

- publish/consume event theo `EventEnvelope`
- retry và DLQ flow qua Kafka
- idempotency cho event handler
- reliable publishing bằng outbox MySQL
- quota/reservation theo `reserve -> confirm -> release`
- một business demo gần với thực tế hơn qua flash-sale reservation
- metrics, tracing và actuator để quan sát runtime

`lsf-example` phù hợp để minh họa framework capability. Nó không phải production blueprint hoàn chỉnh và cũng không nên được dùng làm bằng chứng thay cho consumer integration nhiều service.

## Dependency baseline

Module hiện dùng các starter và library chính sau:

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-observability-starter`
- `lsf-outbox-mysql-starter`
- `lsf-outbox-admin-starter`
- `lsf-quota-streams-starter`

Profile mặc định của app là `outbox-mysql`.

## Cách chạy nhanh

### 1. Khởi động hạ tầng tối thiểu từ repo framework

Tại thư mục gốc `D:\IdeaProjects\lsf-parent-fixed`:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

Nếu muốn chạy cả app container hóa:

```bash
docker compose --profile apps up --build
```

### 2. Chạy app từ Maven

```bash
mvn -pl lsf-example spring-boot:run
```

App mặc định chạy tại:

- `http://localhost:8080`

## Endpoint demo chính

### Eventing / retry / DLQ

- `POST /send`
- `POST /send-dup?eventId=E1`
- `POST /send-fail?eventId=FAIL_E1`
- `POST /send-unknown?eventType=demo.unknown.v1`
- `POST /send-one?eventId=E2`

### Outbox

- `POST /outbox/append?eventId=E_OUTBOX_1`

### Quota API

- `POST /quota/reserve`
- `POST /quota/confirm`
- `POST /quota/release`
- `POST /quota/reserve-json`
- `POST /quota/confirm-json`
- `POST /quota/release-json`

### Flash sale demo

- `POST /demo/flash-sale/orders/reserve`
- `POST /demo/flash-sale/orders/{orderId}/confirm`
- `POST /demo/flash-sale/orders/{orderId}/release`
- `GET /demo/flash-sale/orders/{orderId}`

### Quan sát runtime

- `GET /actuator/health`
- `GET /actuator/prometheus`

## Kịch bản demo gợi ý

### 1. Eventing happy path

1. Gọi `POST /send`
2. Kiểm tra log handler và metrics
3. Mở `actuator/prometheus` để xem counter liên quan dispatcher

### 2. Duplicate event / idempotency

1. Gọi `POST /send-dup?eventId=E_DEMO_DUP_1`
2. Kiểm tra chỉ một logical event được chấp nhận theo store idempotency

### 3. Retry và DLQ

1. Gọi `POST /send-fail?eventId=FAIL_DEMO_1`
2. Quan sát retry
3. Kiểm tra record ở topic `.DLQ`

### 4. Outbox append và background publish

1. Gọi `POST /outbox/append?eventId=E_OUTBOX_DEMO_1`
2. Kiểm tra row outbox được append
3. Theo dõi trạng thái publisher và metrics

### 5. Flash-sale reservation

1. Gọi `POST /demo/flash-sale/orders/reserve`
2. Xác nhận một order
3. Release một order khác
4. Dùng `GET /demo/flash-sale/orders/{orderId}` để đối chiếu trạng thái

Chi tiết flow flash-sale được mô tả thêm tại [FLASH_SALE_QUOTA_DEMO.md](FLASH_SALE_QUOTA_DEMO.md).

## Ghi chú về kiểm thử

- Module có integration tests với Kafka, Redis, MySQL và Testcontainers.
- Một số bài test yêu cầu Docker khả dụng; nếu máy không có Docker hoặc container runtime hợp lệ, `mvn test` có thể fail dù code vẫn build/compile bình thường.
- `lsf-example` nên được dùng như example runtime có kiểm soát, không phải bằng chứng production-ready cho toàn bộ framework.

## Quan hệ với các module khác

- Nếu cần xem cách tạo service mới từ skeleton, dùng [lsf-service-template](../lsf-service-template/README.md).
- Nếu cần xem mức trưởng thành từng module trong toàn framework, xem [docs/MODULE_MATURITY.md](../docs/MODULE_MATURITY.md).
- Nếu cần hiểu capability nào là core, capability nào mới ở mức partial support, xem [docs/PLATFORM_ADOPTION.md](../docs/PLATFORM_ADOPTION.md).
