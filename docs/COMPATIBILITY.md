# Compatibility

## 1. Baseline Matrix

| Layer | Baseline hiện tại | Ghi chú |
|---|---|---|
| Java build/runtime | Java 21 | Maven build phải chạy bằng JDK 21 |
| Maven | Maven 3.9.x | repo đã có enforcer fail fast cho Java baseline |
| Spring Boot | `3.5.7` | là baseline framework hiện tại |
| Spring Cloud | `2025.0.0` | dùng cho các module foundation/gateway/discovery |
| Confluent serializer stack | `7.6.0` | chỉ relevant khi dùng `lsf-kafka-starter` |
| Flyway | `10.22.0` | relevant cho outbox runtimes |
| LSF artifact version | `1.0-SNAPSHOT` | hiện tại vẫn là snapshot-driven workspace baseline |

## 2. Compatibility Contract Của LSF

LSF hiện chỉ hứa compatibility trong phạm vi sau:

- public surface area đã được mô tả ở `docs/PLATFORM_ADOPTION.md`;
- baseline Java / Spring versions ở bảng trên;
- các module `stable` ở `docs/MODULE_MATURITY.md`, trong giới hạn concern mà chúng công khai giải quyết;
- các module `partial support` chỉ có compatibility ở mức use case đã được docs mô tả, không phải blanket guarantee.

LSF hiện chưa hứa:

- binary compatibility cho mọi internal class;
- compatibility ngang nhau cho tất cả database runtimes;
- compatibility production cho các scaffold assets;
- blanket compatibility giữa mọi tổ hợp starter với nhau.

## 3. Compatibility Theo Maturity Bucket

| Bucket | Mức kỳ vọng compatibility |
|---|---|
| `stable` | nên giữ public contract tương đối ổn định; breaking changes cần release note và owner review |
| `partial support` | có thể thay đổi khi docs / use case support thay đổi; adopter phải verify kỹ trên use case thật |
| `experimental / scaffold` | không có promise compatibility ngoài việc vẫn phục vụ mục tiêu minh họa / bootstrap |

## 4. Consumer `ecommerce-backend`: Current Checkpoint

Tại local checkpoint ngày `2026-04-07`, repo `D:\IdeaProjects\ecommerce-backend` có baseline tương thích với framework hiện tại:

- Java 21
- Spring Boot `3.5.7`
- Spring Cloud `2025.0.0`
- `lsf.version=1.0-SNAPSHOT`

Các module LSF đang được consumer kéo vào root `dependencyManagement` hoặc service modules:

- `lsf-contracts`
- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-quota-streams-starter`
- `lsf-outbox-mysql-starter`
- `lsf-outbox-admin-starter`
- `lsf-observability-starter`

Use cases consumer hiện đang chứng minh được:

- event contract sharing
- Kafka publish/consume conventions
- event observability baseline
- quota / reservation path
- MySQL outbox path
- internal outbox admin dependency wiring

## 5. Những gì checkpoint với `ecommerce-backend` chưa chứng minh

Checkpoint hiện tại chưa nên được hiểu là xác nhận cho các module sau:

- `lsf-config-starter`
- `lsf-discovery-starter`
- `lsf-gateway-starter`
- `lsf-http-client-starter`
- `lsf-kafka-admin-starter`
- `lsf-outbox-postgres-starter`
- `lsf-resilience-starter`
- `lsf-saga-starter`
- `lsf-security-starter`
- `lsf-service-web-starter`

Nó cũng chưa xác nhận:

- gateway path;
- sync HTTP path trọn bộ;
- PostgreSQL outbox path;
- saga/orchestration path;
- blanket compatibility của admin endpoints trong production topology.

## 6. Guidance Cho Consumer Hiện Tại

Đối với `D:\IdeaProjects\ecommerce-backend`, guidance an toàn hiện tại là:

- tiếp tục dùng tập module đã qua checkpoint cho các concern tương ứng;
- không tự động mở rộng sang partial modules nếu chưa có focused validation riêng;
- ưu tiên chuyển cách quản lý version sang BOM import `lsf-parent` khi owner chốt thời điểm phù hợp;
- giữ Java 21, Spring Boot `3.5.7` và Spring Cloud `2025.0.0` đồng bộ với framework.

## 7. Compatibility Và Verification Notes

Các ghi chú quan trọng khi đọc compatibility:

- `mvn -DskipTests verify` là tín hiệu tốt cho reactor/build alignment, nhưng không tự nó chứng minh runtime compatibility.
- full `mvn verify` hiện vẫn có thể vấp ở một số test môi trường hoặc DB/container-sensitive ngoài phạm vi docs contract.
- compatibility checkpoint với consumer mang giá trị cao hơn đối với các module đang thật sự được service adopter dùng.

## 8. Owner Decisions Needed

- Chốt có duy trì một compatibility matrix chính thức giữa LSF và `ecommerce-backend` hay không.
- Chốt cadence cập nhật checkpoint: theo mỗi release candidate, theo từng phase, hay theo milestone thủ công.
- Chốt thời điểm consumer phải chuyển sang BOM import `lsf-parent` để giảm drift version.
