# lsf-service-template

Module này cung cấp một service scaffold có thể copy/adapt khi team muốn tạo microservice mới trên framework LSF.

Nó không cố trở thành một business demo đầy đủ. Thay vào đó, nó chỉ ra cách một service mới nên được lắp ghép từ các module framework hiện có:

- dependency baseline
- package/project structure
- actuator/health setup
- security baseline
- sync HTTP client usage
- Kafka producer/consumer usage
- correlation propagation
- outbox-aware publishing
- saga participant reply pattern

## Khi nào nên dùng

Dùng module này khi bạn muốn tạo service mới và cần một điểm xuất phát rõ ràng thay vì ghép tay từng starter.

Nó phù hợp nhất cho:

- service CRUD/event-driven cơ bản có REST ingress
- service cần gọi sync HTTP sang service khác
- service publish event ra Kafka
- service consume event qua `@LsfEventHandler`
- service là downstream participant trong saga/event workflow

## Cấu trúc module

```text
src/main/java/com/myorg/lsf/template
|-- api/
|-- application/
|-- config/
|-- integration/http/
|-- messaging/
|-- support/
`-- workflow/
```

Ý nghĩa các nhóm chính:

- `api/`: REST controller, request/response models
- `application/`: application service orchestration
- `config/`: typed service properties
- `integration/http/`: `@LsfHttpClient` và gateway wrapper
- `messaging/`: producer + consumer examples
- `workflow/`: ví dụ service tham gia saga bằng command/reply event

## Dependency baseline

`pom.xml` của module đã include baseline sau:

- `lsf-service-web-starter`
- `lsf-security-starter`
- `lsf-discovery-starter`
- `lsf-http-client-starter`
- `lsf-resilience-starter`
- `lsf-kafka-starter`
- `lsf-eventing-starter`
- `lsf-outbox-core`
- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`

Với outbox runtime, service thật nên chọn một module theo database:

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-mysql-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```

hoặc

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-postgres-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Những gì scaffold này minh họa

### 1. Sync HTTP client

- `TemplateDependencyClient` dùng `@LsfHttpClient` + `@HttpExchange`
- service discovery dùng `dependency-service`
- resilience/auth/header propagation đi qua `lsf-http-client-starter`

### 2. REST ingress + correlation context

- `TemplateInternalController` expose endpoint cơ bản
- endpoint `/internal/template/context` cho thấy `LsfRequestContextHolder`
- `lsf-service-web-starter` tự manage `correlation-id`, `causation-id`, `request-id`

### 3. Kafka producer + outbox-aware publishing

- `TemplateIntegrationEventPublisher` minh họa 2 path:
  - có `OutboxWriter` -> append vào outbox
  - chưa có `OutboxWriter` -> publish trực tiếp qua `LsfPublisher`
- metadata correlation/request được lấy từ `LsfRequestContextHolder`

### 4. Kafka consumer

- `TemplateInboundEventHandlers` minh họa consumer theo `@LsfEventHandler`
- không cần tự viết `@KafkaListener` ở service adopter

### 5. Saga participation

- `TemplateWorkflowParticipantHandlers` minh họa pattern downstream participant
- service nhận `template.workflow.step.requested.v1`
- service trả `template.workflow.step.completed.v1`
- `correlationId`, `causationId`, `requestId` được preserve khi publish reply

## Cấu hình mẫu

- `application.yml`
  - baseline actuator + probes
  - API key security
  - static discovery cho local/dev
  - HTTP client timeouts + resilience
  - Kafka/eventing topics
- `application-outbox-mysql.yml`
  - block cấu hình outbox cho MySQL
- `application-outbox-postgres.yml`
  - block cấu hình outbox cho PostgreSQL

## Cách team tạo service mới từ template

1. Copy `lsf-service-template` thành module mới, ví dụ `customer-service`.
2. Đổi `artifactId`, package root, `spring.application.name`, và các event/service ids.
3. Giữ nguyên baseline framework dependencies, sau đó chọn thêm một outbox runtime theo database.
4. Thay controller/request/response placeholder bằng API thật của service.
5. Thay `TemplateDependencyClient` bằng HTTP client interface tới downstream service thật.
6. Thay event payloads/event types trong `messaging/` và `workflow/` bằng contracts của domain.
7. Nếu service không dùng saga participant hoặc outbox, xóa hoặc tắt phần scaffold không cần.

## Ghi chú

- Module này là scaffolding/reference implementation, không nhằm thay thế `lsf-example`.
- `lsf-example` vẫn phù hợp để xem flow business nhiều hơn.
- `lsf-service-template` phù hợp hơn khi bạn muốn biết “service mới nên bắt đầu từ đâu”.

## Docker và deployment baseline

Từ Phase 6, module này đi kèm:

- `Dockerfile` multi-stage để build image từ root multi-module repo
- `application-docker.yml` cho local/container network baseline
- Helm skeleton chung ở `ops/deployment/helm/lsf-service`

Khi adopter copy module này thành service mới:

1. đổi module name trong `Dockerfile` nếu không còn dùng tên `lsf-service-template`
2. đổi `SPRING_PROFILES_ACTIVE`, image name, và env vars theo runtime thật
3. dùng chart Helm như skeleton, không coi đó là production chart hoàn chỉnh cho mọi môi trường
