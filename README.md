# LSF

> Một framework Java/Spring Boot được phát triển trong quá trình làm luận văn, đóng gói các khối chức năng có thể tái sử dụng cho microservices hướng sự kiện, và đã được kiểm chứng một phần thông qua tích hợp vào một hệ thống ecommerce đóng vai trò consumer.

LSF là một repository framework theo mô hình multi-module, tập trung vào những concern thường bị viết đi viết lại trong hệ phân tán: khởi tạo Kafka, shared event contracts, cơ chế dispatch sự kiện theo envelope, idempotent handling, reservation/quota workflow, phát hành sự kiện theo outbox, và khả năng quan sát trong vận hành.

Repository này **không** được giới thiệu như một enterprise platform hoàn chỉnh hay một framework “giải quyết mọi thứ”. Thay vào đó, đây là một dự án framework mang tính thực tiễn được xây dựng trong quá trình làm luận văn, trong đó một số module đã được đem tích hợp vào một hệ ecommerce microservices để làm consumer kiểm chứng.

---

## Vì sao dự án này tồn tại?

Trong các hệ microservices hướng sự kiện, đội phát triển thường lặp lại cùng một loại công việc hạ tầng ở từng service:

- tự cấu hình Kafka producer và consumer từ đầu
- tự quyết định cách chuẩn hóa metadata và shape của event
- liên tục tự cài retry, DLQ, và error classification
- xử lý duplicate event và side effect do retry theo kiểu chắp vá
- giải quyết bài toán reservation tài nguyên theo cách riêng ở từng service
- publish domain event một cách thiếu tin cậy sau khi cập nhật database
- bổ sung log, metrics, tracing quá muộn trong dự án

Sự lặp lại này làm hệ thống khó phát triển và khó đồng bộ về sau.

LSF ra đời để gom các concern lặp lại đó thành các module tái sử dụng, giúp team tập trung hơn vào business logic thay vì cứ phải xây lại cùng một tầng eventing cho mỗi service.

Ví dụ với ecommerce thì động lực này rất rõ:

- **Quota / reservation** giúp hạn chế oversell trong các luồng đặt hàng có cạnh tranh cao.
- **Outbox** giúp tránh tình huống kinh điển “DB đã update nhưng publish Kafka thất bại”.
- **Eventing theo envelope** giúp chuẩn hóa contract và cách đăng ký handler.
- **Observability hooks** giúp team nhìn được điều gì đã xảy ra khi luồng xử lý chuyển sang bất đồng bộ.

---

## Ý tưởng cốt lõi và mục tiêu thiết kế

LSF được tổ chức xoay quanh một số mục tiêu thực tế:

1. **Chuẩn hóa tích hợp Kafka**
   - dùng chung producer/consumer defaults
   - hỗ trợ retry/DLQ
   - tái sử dụng cấu hình serialization

2. **Chuẩn hóa shape của event**
   - dùng `EventEnvelope` để gói metadata + payload
   - thống nhất convention cho event type, headers, correlation, aggregate identity

3. **Giảm boilerplate khi xử lý event**
   - mô hình handler theo annotation với `@LsfEventHandler`
   - payload conversion + registry + dispatcher abstraction
   - có thể bật sẵn idempotency

4. **Hỗ trợ reservation workflow một cách tường minh**
   - `reserve -> confirm -> release`
   - quota state chạy trên memory/Redis
   - hỗ trợ policy lookup và caching

5. **Publish event tin cậy hơn**
   - outbox writer + background publisher
   - có lựa chọn runtime cho MySQL và PostgreSQL
   - có thể bật admin endpoints để inspect/requeue

6. **Cải thiện khả năng quan sát khi vận hành**
   - metrics wrappers
   - helper cho MDC/context propagation
   - observing hooks quanh dispatcher

7. **Trung thực về phạm vi đã được kiểm chứng**
   - có module đã được validate trong consumer ecommerce
   - có module mới ở mức example
   - có module rõ ràng mang tính future-facing scaffolding

---

## Tổng quan các module

| Module | Mục đích | Giải quyết vấn đề gì? | Tình huống dùng điển hình | Mức độ kiểm chứng trong consumer ecommerce |
|---|---|---|---|---|
| `lsf-kafka-starter` | Auto-configure Kafka producer/consumer defaults, retry/DLQ, Serde support | Tránh phải bootstrap Kafka lặp lại và error handling không đồng nhất | Bất kỳ service nào publish hoặc consume Kafka messages | **Đã kiểm chứng** |
| `lsf-contracts` | Cung cấp shared contracts như `EventEnvelope` và reservation commands | Tránh event shape không thống nhất và định nghĩa contract bị lặp | Dùng chung event model giữa các service | **Đã kiểm chứng** |
| `lsf-eventing-starter` | Cung cấp envelope listener, handler registry, payload conversion, dispatcher, idempotency support | Giảm boilerplate ở Kafka listener và dispatch logic | Service muốn xử lý event theo kiểu handler | **Kiểm chứng một phần** |
| `lsf-saga-redis-starter` | Module định hướng cho saga/state trên Redis | Hướng tới bài toán orchestration/state trong tương lai | Công việc workflow/state management về sau | **Future work / chưa được kiểm chứng đáng kể** |
| `lsf-quota-streams-starter` | Triển khai quota/reservation workflow với memory/Redis state và policy provider | Giải quyết oversell, overbooking, reserve/confirm/release rời rạc | Giữ hàng tồn, đặt chỗ, flash sale, booking slot | **Đã kiểm chứng** |
| `lsf-observability-starter` | Bổ sung dispatcher metrics, MDC/context helpers, observing wrapper | Tăng khả năng nhìn thấy luồng async event | Service cần metrics/logging quanh event handling | **Kiểm chứng một phần** |
| `lsf-example` | Ứng dụng demo để minh họa cách các module phối hợp | Giúp người mới nhìn ra đường áp dụng framework | Module tham khảo/học cách dùng | **Chỉ ở mức ví dụ** |
| `lsf-outbox-core` | Tầng abstraction cốt lõi cho outbox writing | Tránh gắn chặt service với outbox code phụ thuộc DB cụ thể | Nền tảng cho service bật outbox | **Gián tiếp được kiểm chứng qua runtime starters** |
| `lsf-outbox-mysql-starter` | Runtime outbox cho MySQL gồm writer + publisher + metrics | Giải quyết dual-write gap giữa DB commit và Kafka publish | Service dùng MySQL cần publish event sau transactional update | **Đã kiểm chứng** |
| `lsf-outbox-postgres-starter` | Runtime outbox cho PostgreSQL | Giải quyết cùng bài toán dual-write cho PostgreSQL | Service dùng PostgreSQL | **Có sẵn, nhưng chưa được validate sâu trong ecommerce consumer** |
| `lsf-outbox-admin-starter` | Admin APIs để list, inspect, requeue, mark failed, delete outbox rows | Hỗ trợ vận hành khi outbox lỗi hoặc cần kiểm tra | Công cụ nội bộ cho ops/admin | **Kiểm chứng một phần** |

---

## Mỗi module đóng góp gì trên thực tế?

### 1) Chuẩn hóa Kafka

`lsf-kafka-starter` gom phần setup producer/consumer để service mới không phải tự cài lại cùng một kiểu cấu hình.

Trong code hiện tại, module này cung cấp:

- producer defaults như `acks=all`, idempotence, retries, compression, linger, batch size
- consumer defaults như managed commits, `max.poll.records`, group id, offset reset behavior
- retry và DLQ được gom chung
- DLQ reason classifier và DLQ headers
- abstraction `SerdeFactory` để chọn serializer/deserializer

Module này phù hợp khi nhiều service muốn có một baseline Kafka chung nhưng vẫn giữ topic và payload phục vụ business riêng.

### 2) Shared event contracts

`lsf-contracts` định nghĩa “ngôn ngữ chung” giữa các service.

Đối tượng trung tâm là `EventEnvelope`, bao gồm:

- `eventId`
- `eventType`
- `version`
- `aggregateId`
- `correlationId`
- `causationId`
- `occurredAtMs`
- `producer`
- `payload`
- `error` nếu có

Cấu trúc đó giúp việc evolve contract rõ ràng hơn và cũng tạo nền để xây công cụ cross-cutting quanh event.

### 3) Abstraction cho event handler

`lsf-eventing-starter` cho phép service đăng ký event handler bằng annotation, thay vì phải viết listener với switch/if lặp đi lặp lại.

Module này bao gồm:

- `@LsfEventHandler`
- `LsfEnvelopeListener`
- `HandlerRegistry`
- `PayloadConverter`
- dispatcher abstractions
- `IdempotentLsfDispatcher`
- in-memory và Redis idempotency stores

Nó hữu ích khi một service consume nhiều loại event và muốn có mô hình handler gọn gàng hơn.

### 4) Quota / reservation workflow

`lsf-quota-streams-starter` đóng gói một pattern thường lặp lại khi xử lý tài nguyên hữu hạn:

- reserve một hold tạm thời
- confirm khi workflow hoàn tất
- release khi thất bại, timeout, hoặc bị hủy

Code hiện tại có:

- `QuotaService`
- `QuotaReservationFacade`
- `QuotaQueryFacade`
- `QuotaRequest`, `QuotaResult`, `QuotaSnapshot`, `QuotaDecision`
- `MemoryQuotaService`
- `RedisQuotaService`
- policy providers và policy cache
- quota metrics

Điều này rất sát với các bài toán như giữ hàng tồn trong ecommerce, đặt ghế, hoặc workflow có giới hạn tài nguyên.

### 5) Outbox publishing

`lsf-outbox-core`, `lsf-outbox-mysql-starter`, và `lsf-outbox-postgres-starter` giải quyết bài toán dual-write.

Thay vì làm:

1. update database
2. publish event trực tiếp

một service bật outbox sẽ làm:

1. update database
2. append `EventEnvelope` vào `lsf_outbox` trong cùng transaction
3. để background publisher gửi event ra Kafka

Runtime cho MySQL hiện có:

- `JdbcOutboxWriter`
- `JdbcOutboxRepository`
- `OutboxPublisher`
- scheduling values
- publisher hooks
- metrics
- Flyway SQL cho bảng outbox và logic retry/lease

Runtime cho PostgreSQL cũng theo cùng ý tưởng cho service dùng PostgreSQL.

### 6) Hỗ trợ vận hành

`lsf-observability-starter` và `lsf-outbox-admin-starter` tập trung vào phần “nhìn thấy và vận hành như thế nào”.

Observability support bao gồm:

- `LsfContext`
- `LsfMdc`
- `LsfMetrics`
- `ObservingLsfDispatcher`

Outbox admin support cung cấp REST endpoints để:

- list/filter outbox rows
- inspect theo id hoặc event id
- requeue rows bị lỗi
- mark rows failed
- delete rows nếu được bật rõ ràng

---

## LSF được thiết kế để dùng như thế nào?

Có hai hướng áp dụng phổ biến.

### Hướng A — xây service mới trên nền LSF

Team bắt đầu service mới có thể chọn đúng các module mình cần.

#### Trường hợp A1: service chỉ cần Kafka + shared contracts

Dùng:

- `lsf-kafka-starter`
- `lsf-contracts`

Như vậy service có event shape chung và producer/consumer defaults có thể tái sử dụng.

#### Trường hợp A2: service consume nhiều loại event và muốn dispatch theo handler

Thêm:

- `lsf-eventing-starter`

Khi đó team có thể viết business handler với `@LsfEventHandler` thay vì tự route message.

#### Trường hợp A3: service quản lý tài nguyên hữu hạn

Thêm:

- `lsf-quota-streams-starter`

Phù hợp với inventory hold, booking slot, coupon cap, hoặc bất kỳ workflow nào cần reserve/confirm/release.

#### Trường hợp A4: service publish domain event sau transactional DB update

Thêm:

- `lsf-outbox-core`
- một runtime starter: `lsf-outbox-mysql-starter` hoặc `lsf-outbox-postgres-starter`

Có thể thêm tiếp:

- `lsf-outbox-admin-starter`
- `lsf-observability-starter`

### Hướng B — tích hợp LSF vào hệ microservices có sẵn

LSF cũng có thể được đưa vào dần dần trong một hệ thống đã tồn tại.

Một chiến lược migration thực tế là:

1. **chuẩn hóa Kafka setup trước**
   - thay config producer/consumer lặp lại bằng `lsf-kafka-starter`

2. **đưa shared event contracts vào sau**
   - bắt đầu gói một số event bằng `EventEnvelope`

3. **đưa quota/reservation vào nơi có contention cao nhất**
   - ví dụ inventory hold thay vì trừ stock ngay lập tức

4. **đưa outbox vào nơi transactional reliability là quan trọng**
   - bắt đầu với các domain event giá trị cao như order status changes

5. **thêm handler abstraction và observability khi nó giúp code dễ bảo trì hơn**
   - đặc biệt ở notification service hoặc các service phối hợp theo event

Cách tiếp cận từng bước này cũng chính là cách framework được đem ra kiểm chứng trong consumer ecommerce.

---

## Ví dụ cách dùng

Việc chọn module cụ thể phụ thuộc vào vai trò của từng service, nhưng các đoạn sau cho thấy trải nghiệm mà framework hướng tới cho developer.

### Thêm dependency starter

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

### Cấu hình Kafka tối thiểu

```yaml
lsf:
  kafka:
    bootstrap-servers: localhost:9092
    schema-registry-url: http://localhost:8081
    consumer:
      group-id: my-service-group
      batch: false
      concurrency: 1
      max-poll-records: 50
      retry:
        attempts: 3
        backoff: 1000ms
    dlq:
      enabled: true
      suffix: ".DLQ"
```

### Đăng ký event handler

```java
@Component
public class OrderHandlers {

    @LsfEventHandler(value = "ecommerce.order.status.v1", payload = OrderStatusEvent.class)
    public void onOrderStatus(EventEnvelope envelope, OrderStatusEvent event) {
        // business logic
    }
}
```

### Cấu hình topic cho eventing listener

```yaml
lsf:
  eventing:
    consume-topics:
      - order-status-envelope-topic
    ignore-unknown-event-type: false
    idempotency:
      enabled: true
      store: auto
      ttl: PT24H
```

### Publish một event theo dạng envelope

```java
publisher.publish(
    "order-status-envelope-topic",
    orderNumber,
    "ecommerce.order.status.v1",
    orderNumber,
    payload
);
```

Ở tầng dưới, publisher mặc định sẽ tự bọc payload vào `EventEnvelope` trước khi gửi.

### Reserve inventory bằng quota

```java
QuotaResult result = quotaService.reserve(
    QuotaRequest.builder()
        .quotaKey("shopA:flashsale_sku:SKU-001")
        .requestId("order-1001:SKU-001")
        .amount(2)
        .limit(100)
        .hold(Duration.ofSeconds(120))
        .build()
);
```

### Confirm hoặc release reservation

```java
quotaService.confirm("shopA:flashsale_sku:SKU-001", "order-1001:SKU-001");
quotaService.release("shopA:flashsale_sku:SKU-001", "order-1001:SKU-001");
```

### Append event vào outbox trong transaction

```java
@Transactional
public void updateOrderAndPublish(OrderStatusChanged payload) {
    EventEnvelope envelope = EnvelopeBuilder.wrap(
        objectMapper,
        "ecommerce.order.status.v1",
        1,
        payload.orderNumber(),
        payload.orderNumber(),
        null,
        "order-service",
        payload
    );

    // update domain data here

    outboxWriter.append(envelope, "order-status-envelope-topic", payload.orderNumber());
}
```

### Bật outbox runtime

```yaml
lsf:
  outbox:
    enabled: true
    table: lsf_outbox
    publisher:
      enabled: true
      scheduling-enabled: true
      batch-size: 50
      poll-interval: 1s
      initial-delay: 1s
      claim-strategy: SKIP_LOCKED
```

### Bật outbox admin endpoints

```yaml
lsf:
  outbox:
    admin:
      enabled: true
      allow-delete: false
```

---

## Kiểm chứng trong ecommerce: trước và sau

Một mục tiêu lớn của repository này là cho thấy framework không chỉ được thiết kế trên lý thuyết.

Nhiều module đã được đem tích hợp vào một hệ ecommerce microservices đóng vai trò consumer, đặc biệt quanh luồng order–inventory–payment.

### Những phần đã được kiểm chứng rõ

- `lsf-kafka-starter`
- `lsf-contracts`
- `lsf-quota-streams-starter`
- `lsf-outbox-mysql-starter`
- `lsf-eventing-starter` ở mức nhỏ nhưng có tích hợp thật trong `notification-service`
- observability/admin support ở mức evidence/demo

### Trước khi tích hợp

- Kafka setup thiên về cấu hình riêng lẻ theo từng service và kém chuẩn hóa hơn.
- Inventory logic gần với kiểu trừ stock trực tiếp.
- Order status publishing dựa nhiều hơn vào direct send.
- Status topics gắn chặt hơn với event shape cũ.

### Sau khi tích hợp

- Kafka configuration được chuẩn hóa một phần qua `lsf-kafka-starter`.
- Inventory flow được refactor theo hướng **reserve -> confirm -> release**.
- Order status publishing chuyển sang **append vào outbox -> publisher gửi Kafka**.
- Status events được bọc trong `EventEnvelope`.
- Một envelope topic riêng được thêm vào để hỗ trợ contract evolution sạch hơn.
- Notification/event consumer logic có thể dùng handler-style dispatch với `@LsfEventHandler`.

### Ví dụ tích hợp cụ thể

#### Inventory service

Phần tích hợp ở consumer tạo một lớp adapter để ánh xạ identity của order/SKU trong ecommerce thành quota keys và request ids, rồi gọi xuống `QuotaService`.

Ý nghĩa business:

- `reserve(...)` dùng khi đơn hàng bắt đầu giữ hàng
- `confirm(...)` dùng khi thanh toán thành công
- `release(...)` dùng khi thanh toán thất bại hoặc workflow bị hủy

#### Order service

Phần tích hợp ở consumer chuyển việc publish status sang outbox flow:

- lưu/cập nhật order state
- append `EventEnvelope` vào `lsf_outbox`
- để outbox publisher gửi bất đồng bộ

Cách này cải thiện độ tin cậy hơn so với publish trực tiếp ngay sau thao tác DB.

#### Notification service

Một điểm validate nhỏ nhưng thật cho `lsf-eventing-starter` xuất hiện trong luồng notification của consumer, nơi một event handler xử lý order status event theo dạng envelope bằng `@LsfEventHandler`.

### Vì sao việc kiểm chứng này quan trọng?

Hệ ecommerce không được dùng để tuyên bố rằng mọi module đều đã production-hardened. Điều nó **cho thấy** là các module của LSF có thể được tích hợp vào một ứng dụng phân tán có thật và hỗ trợ những thay đổi workflow mang ý nghĩa thực tế, chứ không chỉ là ví dụ.

---

## Kịch bản kiểm chứng và evidence

Repository này cùng với phần consumer integration đã được dùng để kiểm tra nhiều loại hành vi khác nhau.

### Kịch bản chức năng

- happy path: reservation accepted -> payment succeeds -> reservation confirmed
- failure path: reservation accepted -> payment fails -> reservation released
- rejection path: reservation vượt quota -> request bị reject
- event publishing path: status event được append vào outbox rồi mới publish
- handler path: envelope event được dispatch tới handler method

### Kịch bản stress / contention

Quota flow đặc biệt phù hợp với các bài test kiểu contention như:

- hot SKU / flash-sale traffic
- race-condition style concurrent reservation attempts
- acceptance bị chặn trong giới hạn tài nguyên cố định

### Các khối evidence nên chèn thêm khi public repo

Bạn có thể thêm screenshot hoặc chart vào section này khi chuẩn bị đẩy GitHub:

- `[TODO: Chèn biểu đồ benchmark tổng quan ở đây — so sánh baseline flow và flow đã tích hợp quota/outbox]`
- `[TODO: Chèn bảng kết quả test race-condition / hot-SKU ở đây — accepted vs rejected dưới tải cạnh tranh]`
- `[TODO: Chèn ảnh Grafana hoặc Actuator metrics ở đây — quota/outbox/event processing metrics]`
- `[TODO: Chèn ảnh outbox admin page ở đây — evidence inspect/requeue]`
- `[TODO: Chèn ảnh waiting page / order progress ở đây — evidence giao diện cho async workflow]`
- `[TODO: Chèn sequence diagram order-inventory-payment ở đây — mô tả luồng kiểm chứng rút gọn]`

> Hãy giữ các hình ảnh này tập trung vào thay đổi kỹ thuật: reservation lifecycle, outbox lifecycle, và operational visibility.

---

## Phạm vi hiện tại và các giới hạn

Dự án này được chủ động giới hạn phạm vi và nên được mô tả trung thực.

### Những gì LSF hiện đang thể hiện tốt

- cấu trúc framework dạng starter có thể tái sử dụng cho microservices hướng sự kiện
- shared contract model thông qua `EventEnvelope`
- quota/reservation như một reusable building block
- runtime support cho outbox trên MySQL và PostgreSQL
- lộ trình áp dụng thực tế thông qua consumer integration

### Những gì không nên nói quá

- đây **không** phải là một platform hoàn chỉnh bao phủ mọi concern của microservices
- không phải module nào cũng được validate ở cùng một độ sâu
- phần tích hợp ecommerce chỉ validate các workflow được chọn, không phải mọi tình huống
- business orchestration đặc thù của consumer vẫn thuộc về chính consumer project
- Kafka Streams topologies và các domain-specific flow khác chưa được LSF trừu tượng hóa hoàn toàn
- outbox cho PostgreSQL có tồn tại trong framework, nhưng mức chứng minh tính portable hiện vẫn nhẹ hơn câu chuyện validate bằng MySQL
- `lsf-saga-redis-starter` hiện mới mang tính khung định hướng cho tương lai hơn là một runtime component đã có nhiều evidence

### Ghi chú vận hành

Với outbox schema ownership, một cách tích hợp thực tế là vẫn để migration versioning nằm trong consumer project thay vì xem framework là chủ sở hữu toàn bộ Flyway history của consumer.

---

## Roadmap / hướng phát triển tiếp theo

Codebase hiện tại đã tạo nền tốt cho một số bước tiếp theo:

1. **Kiểm chứng eventing sâu hơn**
   - mở rộng tích hợp handler-based ra ngoài use case notification nhỏ hiện tại

2. **Tăng cường benchmark và resilience testing**
   - load test rộng hơn
   - thêm fault-injection scenarios
   - công bố benchmark artifacts rõ ràng hơn

3. **Mở rộng operational tooling**
   - làm giàu khả năng cho outbox admin
   - thêm dashboard và metrics phục vụ alerting

4. **Chứng minh portability rộng hơn**
   - validate sâu hơn trên PostgreSQL
   - thêm các consumer system khác ngoài ecommerce

5. **Tiếp tục phát triển saga/state workflow support**
   - đưa Redis saga module hiện tại thành một component được chứng minh rõ hơn

---

## Điều hướng repository

### Bên trong repository này

- `pom.xml` — parent POM và danh sách module
- `lsf-kafka-starter/` — Kafka defaults, retry/DLQ, Serde support
- `lsf-contracts/` — shared envelope và contract definitions
- `lsf-eventing-starter/` — listener/registry/dispatcher/idempotency support
- `lsf-quota-streams-starter/` — quota/reservation APIs và implementations
- `lsf-observability-starter/` — context/MDC/metrics helpers
- `lsf-outbox-core/` — outbox abstraction không phụ thuộc DB cụ thể
- `lsf-outbox-mysql-starter/` — MySQL outbox runtime
- `lsf-outbox-postgres-starter/` — PostgreSQL outbox runtime
- `lsf-outbox-admin-starter/` — admin/ops endpoints cho outbox
- `lsf-saga-redis-starter/` — Redis saga/state module hướng tới tương lai
- `lsf-example/` — ứng dụng ví dụ nhỏ và demo flows

### Các external link nên thêm khi public

- `[TODO: Thêm link tới ecommerce backend validation repository]`
- `[TODO: Thêm link tới ecommerce frontend validation repository]`
- `[TODO: Thêm link tới integration notes / tài liệu before-after]`
- `[TODO: Thêm ảnh demo hoặc link video demo ngắn]`

### Nên bắt đầu từ đâu nếu bạn mới vào repo?

- đọc README root này trước
- mở `lsf-example/` để xem cách các module được nối với nhau
- mở `lsf-quota-streams-starter/README.md` và `lsf-example/FLASH_SALE_QUOTA_DEMO.md` để xem ví dụ business-facing rõ nhất
- sau đó xem các outbox runtime modules nếu bạn quan tâm chính tới reliable event publishing

---

## Bắt đầu nhanh

### Yêu cầu trước

- Java 21
- Maven
- Docker, nếu bạn muốn chạy test dùng Testcontainers hoặc local dependencies phục vụ demo

### Build toàn bộ

```bash
mvn clean install
```

### Build bỏ qua test

```bash
mvn clean install -DskipTests
```

### Chạy example application

```bash
mvn -pl lsf-example spring-boot:run
```

### Ghi chú local

Example app hiện có các pattern cấu hình cho:

- Kafka + Schema Registry
- Redis-backed idempotency/quota state
- profile dùng MySQL-backed outbox
- Actuator/metrics exposure

Trên thực tế, bạn nên bắt đầu bằng cách đọc module example thay vì cố chạy từng module độc lập.

---

## Khi nào nên dùng module nào?

Một quy tắc đơn giản:

- chọn **`lsf-kafka-starter`** khi bạn muốn có baseline Kafka chuẩn hóa
- thêm **`lsf-contracts`** khi nhiều service cần thống nhất event shape
- thêm **`lsf-eventing-starter`** khi consumer cần handler-style dispatch và idempotency tùy chọn
- thêm **`lsf-quota-streams-starter`** khi business flow cạnh tranh cùng một tài nguyên hữu hạn
- thêm **một outbox runtime starter** khi service publish event sau transactional DB changes
- thêm **`lsf-outbox-admin-starter`** khi operator cần inspect/requeue tooling
- thêm **`lsf-observability-starter`** khi async debugging và metrics cần được quan tâm sớm

Không phải service nào cũng cần tất cả module.

Đó là chủ đích thiết kế.

---

## Kết luận

LSF nên được hiểu như một tầng framework có thể tái sử dụng cho các concern phổ biến trong microservices hướng sự kiện, được phát triển với chiều sâu của một đề tài luận văn và có điểm tựa thực tế là việc tích hợp một phần vào một consumer system không quá đơn giản.

Giá trị của nó không nằm ở việc tuyên bố giải quyết mọi thứ. Giá trị của nó nằm ở chỗ biến một số pattern hạ tầng quan trọng — chuẩn hóa Kafka, shared contracts, event dispatch, reservation flow, reliable publishing, và operational visibility — thành các module có thể dùng lại và áp dụng dần từng bước.

Nếu bạn đang xem repository này với vai trò engineer, mentor, hoặc recruiter, điều chính nên rút ra là: đây không chỉ là bài toán thiết kế framework, mà còn là nỗ lực cho thấy framework đó có thể được tích hợp, kiểm chứng và phát triển tiếp trong một ứng dụng phân tán có độ phức tạp thực tế.
