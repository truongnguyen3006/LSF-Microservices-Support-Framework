# lsf-observability-starter

Starter này bổ sung observability cho event dispatch trong LSF: metrics, MDC và observation hooks quanh `LsfDispatcher`.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-observability-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Những gì starter cung cấp

- tự wrap mọi `LsfDispatcher` bằng `ObservingLsfDispatcher` qua `BeanPostProcessor`
- MDC fields phục vụ log correlation theo envelope metadata và topic
- metrics cơ bản cho outcomes và latency, ví dụ:
  - `lsf.event.handled.success`
  - `lsf.event.handled.fail`
  - `lsf.event.duplicate`
  - `lsf.event.in_flight`
  - `lsf.event.processing`
- alias metrics tương thích ngược cho retry/DLQ:
  - `lsf.kafka.retry`
  - `lsf.kafka.dlq`
- integration với `ObservationRegistry` nếu classpath đã có observation support

## Cấu hình ví dụ

```yaml
lsf:
  observability:
    enabled: true
    mdc-enabled: true
    metrics-enabled: true
    tracing-enabled: true
    tag-topic: true
    tag-event-type: true
    tag-outcome: true
    tag-event-id: false
```

## Cách adopter dùng starter

- Chỉ cần thêm dependency vào service đã dùng `lsf-eventing-starter`.
- Nếu muốn export metrics, service cần có `MeterRegistry` phù hợp.
- Nếu muốn trace/observation integration, service cần thêm bridge/exporter tương ứng của Micrometer.

Ví dụ exporter cho Zipkin:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<dependency>
  <groupId>io.zipkin.reporter2</groupId>
  <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

## Ghi chú thiết kế

- Starter này tập trung vào event dispatch path, không cố trở thành observability platform hoàn chỉnh.
- Các tag mặc định tránh cardinality cao; `eventId` không được tag mặc định.
- Metrics được pre-register để các endpoint actuator không trả `404` trước khi event đầu tiên đi qua hệ thống.

## Validation hiện có

- có cross-module runtime test tối thiểu ghép `lsf-kafka-starter` + `lsf-eventing-starter` + `lsf-observability-starter`
- suite này verify publish event, consume qua dispatcher, propagation của `correlationId` / `causationId` / `requestId`, cùng metrics và observation

## Giới hạn hiện tại

- phạm vi chính vẫn là `LsfDispatcher`, chưa bao phủ mọi loại runtime trong repo
- không tự cấu hình metrics exporter hoặc trace backend
- chưa mở rộng thành bộ cross-module runtime lớn cho mọi biến thể consumer/runtime trong repo
