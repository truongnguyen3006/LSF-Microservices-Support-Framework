# LSF

LSF là một repository framework Java/Spring Boot theo mô hình multi-module, được xây dựng để hỗ trợ phát triển microservices quy mô lớn theo hướng thực dụng. Trọng tâm của repo là gom các concern lặp lại giữa nhiều service thành các starter và asset có thể tái sử dụng: shared contracts, eventing, retry/DLQ, outbox, sync HTTP conventions, quota/reservation, orchestration, observability, cùng baseline vận hành và triển khai.

Repository này được định vị như một framework dùng cho luận văn, không phải một platform hoàn chỉnh hay một bộ sản phẩm “giải quyết mọi thứ” cho microservices. Các module có mức hoàn thiện khác nhau; vì vậy tài liệu ưu tiên mô tả đúng trạng thái hiện có, chỉ rõ phần nào đã hỗ trợ tốt, phần nào mới ở mức partial support, scaffold hoặc future work.

## Framework này hiện cung cấp gì?

| Capability area | Modules | Vai trò ở mức framework | Trạng thái hiện tại |
|---|---|---|---|
| Core contracts | `lsf-contracts` | Chuẩn hóa `EventEnvelope`, request/trace context, retry classification, sync error model và quota contracts | Core library, được nhiều starter dùng lại |
| Platform foundations | `lsf-config-starter`, `lsf-discovery-starter`, `lsf-gateway-starter`, `lsf-security-starter`, `lsf-resilience-starter` | Cấu hình tập trung, service resolution, gateway conventions, auth baseline, retry/circuit/timeout/rate-limit | Starter-level support, không thay thế server hoặc platform bên ngoài |
| Sync service interaction | `lsf-service-web-starter`, `lsf-http-client-starter` | Chuẩn hóa sync HTTP ingress/egress, canonical headers, `LsfErrorResponse`, `RestClient` proxy, discovery/resilience/auth propagation | Starter-level support, hiện thiên về servlet-based services và đã có minimal cross-module runtime evidence cho path `service-web + http-client + security + resilience` |
| Event-driven runtime | `lsf-kafka-starter`, `lsf-eventing-starter`, `lsf-observability-starter` | Kafka defaults, retry/DLQ, envelope dispatch, idempotency, metrics/MDC/observation quanh event handling | Đã có focused tests và cross-module runtime path cho Kafka + eventing + observability; chưa có full end-to-end broker suite cho mọi luồng |
| Reliable publishing and admin tooling | `lsf-outbox-core`, `lsf-outbox-mysql-starter`, `lsf-outbox-postgres-starter`, `lsf-outbox-admin-starter`, `lsf-kafka-admin-starter` | Outbox append/publisher theo DB, inspect/requeue/delete outbox rows, inspect/replay DLQ records | MySQL được kiểm chứng sâu hơn; Kafka admin đã có broker-backed regression, outbox admin đã có vendor-specific MySQL/PostgreSQL regression, nhưng admin tooling vẫn nên xem là support có kiểm soát |
| Workflow and resource control | `lsf-quota-streams-starter`, `lsf-saga-starter` | Reserve/confirm/release cho tài nguyên hữu hạn, saga orchestration theo event với timeout/compensation | Có runtime và test framework-level; orchestration graph phức tạp hơn vẫn là future work |
| Adoption assets | `lsf-service-template`, `lsf-example`, `ops/`, `.github/`, `docker-compose.yml` | Scaffold service mới, example app, monitoring/deployment baselines, CI/CD skeleton | Scaffold/example assets, không nên diễn giải như production blueprint đầy đủ |

### Cách đọc trạng thái hỗ trợ

- `core library`: module nền dùng làm hợp đồng chung hoặc abstraction cốt lõi.
- `starter-level support`: module runtime dùng được ở mức framework, có auto-configuration và test tương ứng.
- `partial support`: tính năng có thật nhưng chưa được chứng minh sâu bằng full integration/runtime breadth.
- `scaffold/example`: tài sản hướng dẫn áp dụng framework, không phải runtime product độc lập.

## Kiến trúc ở mức cao

LSF được tổ chức theo các lớp concern thay vì theo từng business domain:

```text
Ingress / Edge
  -> lsf-gateway-starter (optional)
  -> lsf-service-web-starter + lsf-security-starter
       -> application code
       -> lsf-http-client-starter + lsf-discovery-starter + lsf-resilience-starter
       -> lsf-quota-streams-starter / lsf-saga-starter
       -> lsf-eventing-starter + lsf-kafka-starter
       -> lsf-outbox-core + lsf-outbox-<db>-starter

Async consumption
  Kafka
    -> lsf-kafka-starter
    -> lsf-eventing-starter
    -> lsf-observability-starter
    -> service handlers

Operations
  -> lsf-kafka-admin-starter
  -> lsf-outbox-admin-starter
  -> ops/monitoring
  -> ops/deployment
```

Chi tiết kiến trúc, lý do từng capability thuộc framework, cách adopter dùng và future work được mô tả ở [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Cách adopter dùng framework

### 1. Service event-driven tối thiểu

Nếu service chỉ cần publish/consume Kafka với shape event thống nhất:

- dùng `lsf-contracts`
- thêm `lsf-kafka-starter`
- thêm `lsf-eventing-starter` khi muốn handler-style dispatch
- thêm `lsf-observability-starter` nếu muốn metrics/MDC/observation quanh dispatcher

### 2. Service cần publish event sau transaction database

Nếu service phải tránh dual-write:

- dùng `lsf-outbox-core`
- chọn đúng runtime: `lsf-outbox-mysql-starter` hoặc `lsf-outbox-postgres-starter`
- thêm `lsf-outbox-admin-starter` khi cần inspect/requeue nội bộ

### 3. Service cần sync HTTP giữa microservices

Nếu service có internal REST APIs hoặc gọi downstream bằng HTTP:

- dùng `lsf-service-web-starter`
- dùng `lsf-http-client-starter`
- dùng lại `lsf-discovery-starter`, `lsf-security-starter`, `lsf-resilience-starter`

### 4. Service có workflow hoặc cạnh tranh tài nguyên

Nếu service cần điều phối nhiều bước hoặc tránh oversell/overbooking:

- dùng `lsf-quota-streams-starter` cho reserve/confirm/release
- dùng `lsf-saga-starter` cho orchestration theo event với timeout và compensation

### 5. Bootstrap service mới trên nền LSF

Nếu muốn bắt đầu nhanh:

- xem `lsf-service-template/` như scaffold
- xem `lsf-example/` như app minh họa cách nhiều starter phối hợp
- dùng `ops/` và `ops/deployment/` như baseline vận hành, không phải production template hoàn chỉnh cho mọi tổ chức

## Validation hiện có

Các vòng hoàn thiện gần đây đã tăng đáng kể độ chín của test cho các module runtime ưu tiên:

- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-observability-starter`
- `lsf-outbox-admin-starter`
- `lsf-kafka-admin-starter`
- `lsf-saga-starter`

Các nhóm test nổi bật hiện có trong repo:

- focused unit tests cho dispatcher, publisher, metrics, idempotency, service/repository logic
- `ApplicationContextRunner` tests cho auto-configuration và fail-fast behavior
- lightweight integration/repository tests với H2 ở nơi phù hợp
- controller tests cho admin endpoints
- tracing/metrics propagation tests quanh event dispatch
- broker-backed Testcontainers regression cho `lsf-kafka-admin-starter` để verify đọc DLQ records, replay single record, replay headers và replay metrics
- cross-module runtime test tối thiểu cho `lsf-kafka-starter` + `lsf-eventing-starter` + `lsf-observability-starter` để verify publish, dispatch, metadata propagation, metrics và observation
- cross-module runtime test tối thiểu cho `lsf-service-web-starter` + `lsf-http-client-starter` + `lsf-security-starter` + `lsf-resilience-starter` để verify API key auth, request/trace propagation, `LsfErrorResponse` decode, retryable retry và non-retryable stop
- vendor-specific MySQL/PostgreSQL regression cho `lsf-outbox-admin-starter`, dùng lại schema từ runtime modules
- runtime integration test mặc định cho `lsf-saga-starter`, dùng `direct transport` + JDBC store + `EmbeddedKafka` để verify sequential success path, reply correlation, metadata propagation và compensation path tối thiểu
- profile `heavy-integration` để gate các suite dùng broker/database thật khỏi lane verify mặc định

Các giới hạn xác thực hiện vẫn cần được nêu rõ:

- chưa có broker-backed end-to-end chain phủ toàn bộ listener failure -> retry -> DLQ -> replay cho mọi module liên quan
- sync HTTP runtime coverage hiện mới chứng minh servlet path tối thiểu với local service locator fixture; chưa phải discovery-specific matrix hay reactive/WebFlux parity
- `lsf-saga-starter` mới có runtime integration cho sequential direct-transport path; outbox transport path vẫn chưa được cover trong phase hiện tại
- vendor-specific regression mới tập trung vào `lsf-outbox-admin-starter`; các lớp DB-sensitive khác chưa có suite tương đương
- `lsf-kafka-admin-starter` auto-configuration tests vẫn tạo `AdminClient` nhắm tới `localhost:9092`, nên có thể xuất hiện warning log nếu máy local không có broker
- các suite container-backed được tách sau profile `heavy-integration`, không chạy trong lane verify mặc định
- một số suite cần Docker/Testcontainers hoặc MySQL cục bộ để chạy trọn vẹn, vì vậy kết quả `mvn test` trên máy mới có thể fail do thiếu hạ tầng chứ không phản ánh framework bị hỏng logic cốt lõi

## Bản đồ tài liệu

- [docs/PLATFORM_ADOPTION.md](docs/PLATFORM_ADOPTION.md): adoption contract ở mức platform, concern map, public surface area và non-goals
- [docs/MODULE_MATURITY.md](docs/MODULE_MATURITY.md): audit toàn bộ module và phân loại `stable` / `partial support` / `experimental-scaffold`
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md): baseline compatibility matrix và note riêng cho consumer `ecommerce-backend`
- [docs/UPGRADING.md](docs/UPGRADING.md): migration path từ custom code sang LSF và guidance khi nâng cấp snapshot/milestone
- [docs/RELEASE_POLICY.md](docs/RELEASE_POLICY.md): release contract, public API expectations, deprecation và owner decisions
- [docs/GOLDEN_PATHS.md](docs/GOLDEN_PATHS.md): các đường áp dụng khuyến nghị cho adopter và Codex rollout
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): kiến trúc tổng thể, diễn tiến capability qua các phase, cách adopter dùng framework và future work
- [lsf-example/README.md](lsf-example/README.md): example application để demo eventing, outbox, quota và flash-sale reservation theo cách gần với consumer hơn scaffold thuần túy
- [lsf-contracts/README.md](lsf-contracts/README.md): shared contracts, headers, request/trace context, sync error model
- [lsf-config-starter/README.md](lsf-config-starter/README.md), [lsf-discovery-starter/README.md](lsf-discovery-starter/README.md), [lsf-gateway-starter/README.md](lsf-gateway-starter/README.md), [lsf-security-starter/README.md](lsf-security-starter/README.md), [lsf-resilience-starter/README.md](lsf-resilience-starter/README.md): các module foundation cho config, discovery, gateway, security và resilience
- [lsf-kafka-starter/README.md](lsf-kafka-starter/README.md): Kafka defaults, retry/DLQ, headers, metrics baseline
- [lsf-eventing-starter/README.md](lsf-eventing-starter/README.md): handler model, listener auto-config, idempotency, publisher API
- [lsf-observability-starter/README.md](lsf-observability-starter/README.md): dispatcher metrics, MDC, observation hooks
- [lsf-outbox-core/README.md](lsf-outbox-core/README.md): outbox abstraction chung cho các runtime theo database
- [lsf-outbox-mysql-starter/README.md](lsf-outbox-mysql-starter/README.md): outbox runtime cho MySQL
- [lsf-outbox-postgres-starter/README.md](lsf-outbox-postgres-starter/README.md): outbox runtime cho PostgreSQL
- [lsf-outbox-admin-starter/README.md](lsf-outbox-admin-starter/README.md): endpoint vận hành outbox
- [lsf-kafka-admin-starter/README.md](lsf-kafka-admin-starter/README.md): inspect/replay DLQ records
- [lsf-quota-streams-starter/README.md](lsf-quota-streams-starter/README.md): quota/reservation capability
- [lsf-saga-starter/README.md](lsf-saga-starter/README.md): orchestration/saga runtime
- [lsf-service-web-starter/README.md](lsf-service-web-starter/README.md): sync HTTP ingress conventions
- [lsf-http-client-starter/README.md](lsf-http-client-starter/README.md): sync HTTP client conventions
- [lsf-service-template/README.md](lsf-service-template/README.md): scaffold để adopter tạo service mới
- [ops/README.md](ops/README.md): monitoring và operations baseline
- [ops/deployment/README.md](ops/deployment/README.md): CI/CD, Docker, Compose và Helm skeleton

## Saga note from consumer evidence

- `lsf-saga-starter` remains a `partial support` module.
- The best-proven runtime path is still `jdbc + direct`.
- Real consumer evidence from `D:\IdeaProjects\ecommerce-backend` justified a narrow public helper for local reply fan-in before a sequential saga step advances.
- That improvement reduces consumer glue without changing the framing of LSF into a general workflow engine.
- Consumer demo hiện tại có thể cấu hình `order-service` mặc định ở `app.order.workflow.mode=lsf-saga`, nhưng đây nên được hiểu là quyết định demo/cutover có kiểm soát ở consumer project, không đồng nghĩa `lsf-saga-starter` đã đạt mức production-ready toàn diện.
- `legacy` vẫn là rollback path hợp lệ ở consumer; framework docs nên tiếp tục mô tả saga như một module đã có bằng chứng runtime hữu ích nhưng chưa đủ rộng để diễn giải như workflow engine tổng quát.

## Quick start

### Yêu cầu

- JDK 21 cho Maven runtime. Chạy `mvn -version` và xác nhận dòng `Java version` là `21.x`; repo sẽ fail fast nếu Maven chạy bằng JDK khác baseline.
- Maven
- Docker nếu muốn chạy compose baseline, Testcontainers hoặc example app với infra phụ trợ

### Verify toàn bộ repo

```bash
mvn clean verify
```

### Install framework vào local Maven repository

Lệnh này phù hợp khi consumer repo như `D:\IdeaProjects\ecommerce-backend` cần dùng snapshot mới nhất của framework:

```bash
mvn clean install
```

### Build bỏ qua test

```bash
mvn clean install -DskipTests
```

### Quản lý version framework ở consumer

Khuyến nghị consumer repo đặt `lsf.version` tập trung ở root POM và import BOM từ `lsf-parent`:

```xml
<properties>
    <java.version>21</java.version>
    <lsf.version>1.0-SNAPSHOT</lsf.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.myorg.lsf</groupId>
            <artifactId>lsf-parent</artifactId>
            <version>${lsf.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Sau đó các module LSF trong consumer không cần khai báo `<version>` riêng cho từng dependency. Nếu consumer dùng snapshot local, hãy chạy `mvn clean install` ở repo framework trước khi build repo consume.

Nếu consumer vẫn dùng `spring-boot-starter-parent`, nên giữ Spring Boot và Spring Cloud cùng baseline với framework hiện tại: `3.5.7` và `2025.0.0`.

### Chạy focused framework regression suites

```bash
mvn -q -pl lsf-kafka-starter,lsf-eventing-starter,lsf-observability-starter,lsf-http-client-starter,lsf-outbox-admin-starter,lsf-kafka-admin-starter,lsf-saga-starter -am test
```

### Chạy heavy integration suites

```bash
mvn -B -ntp -pl lsf-kafka-admin-starter -am -Pheavy-integration verify
mvn -B -ntp -pl lsf-observability-starter -am -Pheavy-integration verify
mvn -B -ntp -pl lsf-outbox-admin-starter -am -Pheavy-integration verify
```

### Chạy example application

```bash
mvn -pl lsf-example spring-boot:run
```

### Validate deployment artifacts

```powershell
pwsh ./ops/deployment/validate.ps1
```

### Chạy local container baseline

```bash
docker compose --profile apps up --build
```

Lệnh compose trên hiện bật các thành phần sau:

- Kafka
- Schema Registry
- MySQL
- Redis
- Zipkin
- `lsf-example`
- `template-service`

Nếu chỉ cần infra nền:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

## Honest scope và future work

LSF hiện phù hợp nhất khi được hiểu là một framework hỗ trợ chuẩn hóa các concern hạ tầng lặp lại trong microservices lớn:

- shared contracts và metadata propagation
- Kafka/eventing conventions
- reliable publishing theo outbox
- sync HTTP conventions
- quota/reservation
- event-driven orchestration
- baseline observability, admin và deployment

LSF hiện chưa nên được mô tả như:

- một platform hoàn chỉnh thay thế toàn bộ Spring Cloud/Kubernetes ecosystem
- một workflow engine tổng quát cho mọi graph orchestration
- một ops suite đầy đủ cho production vận hành đa môi trường
- một framework đã được chứng minh toàn diện bằng full end-to-end runtime tests ở mọi module

Future work quan trọng còn lại:

- full broker-backed end-to-end tests cho listener failure -> retry -> DLQ -> replay trên phạm vi rộng hơn
- outbox transport integration cho `lsf-saga-starter` và matrix runtime sâu hơn giữa saga + eventing + outbox
- regression suites theo vendor cho các lớp DB-sensitive khác ngoài `lsf-outbox-admin-starter`
- mở rộng saga sang branch/parallel join phức tạp hơn
- tăng chiều sâu cho deployment/ops artifacts nếu cần dùng ngoài phạm vi luận văn

