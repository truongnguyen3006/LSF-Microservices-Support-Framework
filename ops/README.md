# Phase 5 Operations Guide

Tài liệu này mô tả cách bật stack vận hành thực dụng cho LSF ở mức framework:

- metrics qua `actuator/prometheus`
- end-to-end tracing qua HTTP/Kafka/saga metadata propagation
- dashboard Grafana và alert rules Prometheus
- DLQ inspection/replay qua `lsf-kafka-admin-starter`
- outbox inspection/requeue qua `lsf-outbox-admin-starter`

Artifact CI/CD, Docker, và Helm skeleton của Phase 6 được tách riêng ở `ops/deployment/README.md`.

## 1. Dependency gợi ý cho service framework-level

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-observability-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>

<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-kafka-admin-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>

<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-admin-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

Nếu service cần export trace thật ra Zipkin, dùng thêm bridge tracing mà repo đã tương thích:

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

## 2. Baseline config

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
    zipkin:
      tracing:
        endpoint: http://localhost:9411/api/v2/spans

lsf:
  observability:
    enabled: true
    tracing-enabled: true

  kafka:
    admin:
      enabled: true
      allow-replay: true

  outbox:
    admin:
      enabled: true
      allow-retry: true
```

## 3. Chạy local monitoring stack

1. Khởi động infra chính của repo:

```bash
docker compose up -d kafka schema-registry mysql redis zipkin
```

2. Khởi động monitoring stack ở thư mục `ops/monitoring`:

```bash
docker compose -f ops/monitoring/docker-compose.monitoring.yml up -d
```

3. Chạy service cần quan sát trên host và expose `http://localhost:<port>/actuator/prometheus`.

4. Mở:

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Zipkin: `http://localhost:9411`

## 4. Assets đi kèm

- Dashboard: `ops/monitoring/grafana/dashboards/lsf-framework-operations.json`
- Datasource provisioning: `ops/monitoring/grafana/provisioning/datasources/prometheus.yml`
- Dashboard provisioning: `ops/monitoring/grafana/provisioning/dashboards/dashboard.yml`
- Prometheus scrape config: `ops/monitoring/prometheus/prometheus.yml`
- Alert rules template: `ops/monitoring/prometheus/alerts/lsf-framework-alerts.yml`

## 5. Playbook vận hành gợi ý

- Nếu `lsf_kafka_dlq_total` tăng:
  inspect record qua `GET /lsf/kafka/dlq/records?topic=<topic>.DLQ`
- Nếu record là transient issue:
  replay qua `POST /lsf/kafka/dlq/replay`
- Nếu `lsf_outbox_pending` tăng liên tục:
  kiểm tra outbox scheduler, Kafka availability, và dùng outbox admin để inspect/requeue
- Nếu `lsf_event_handled_fail_total` tăng:
  đối chiếu `traceId`, `corrId`, `requestId`, `eventId` trong log và Zipkin

## 6. Khuyến nghị triển khai thực tế

- Không expose các admin endpoint này ra public internet.
- Bảo vệ `/lsf/kafka/**` và `/lsf/outbox/**` bằng internal auth hoặc admin-only role.
- `allow-replay` chỉ nên bật ở internal environment hoặc service admin riêng.
- Local/dev có thể sample trace 100%; môi trường lớn hơn nên giảm sampling theo traffic.
- Dashboard/alert trong repo là baseline framework-level, không phải business monitoring suite.
