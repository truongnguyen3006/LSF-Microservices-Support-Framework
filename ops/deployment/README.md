# Phase 6 Deployment Guide

Tài liệu này mô tả baseline CI/CD và deployment được bổ sung ở Phase 6 cho LSF.

Mục tiêu của phase này là chứng minh framework không chỉ dừng ở mức source modules, mà còn có đường dẫn khả thi để:

- verify build trong CI
- đóng gói artifact phục vụ release candidate
- build container image cho app mẫu/scaffold
- cung cấp skeleton triển khai Kubernetes/Helm cho adopter

## 1. Maturity level của artifact

- `.github/workflows/ci.yml`
  - maturity: dùng được ngay cho repository hiện tại
  - phạm vi: Maven verify, validate compose/chart, build sanity cho Docker image

- `.github/workflows/release-candidate.yml`
  - maturity: release-candidate packaging skeleton
  - phạm vi: package JARs, package Helm chart, export image tarballs thành GitHub artifacts
  - giới hạn: chưa publish lên Maven repository hay container registry

- `lsf-example/Dockerfile`
  - maturity: runnable reference image cho app demo của repo

- `lsf-service-template/Dockerfile`
  - maturity: copy/adapt baseline cho adopter khi tạo service mới từ template

- `docker-compose.yml`
  - maturity: local/dev deployment baseline
  - phạm vi: infra cốt lõi, profile chạy app mẫu, profile bật PostgreSQL nếu cần

- `ops/deployment/helm/lsf-service`
  - maturity: Helm skeleton ở mức framework
  - phạm vi: `Deployment`, `Service`, `ServiceAccount`, `Ingress`, `HPA`, probe/resources/env defaults
  - giới hạn: không tự quản secrets, GitOps, service mesh, topic provisioning, hay cloud load balancer tuning

## 2. CI workflow hiện có

### `CI`

Workflow này chạy trên `push`, `pull_request`, và `workflow_dispatch`.

Nó gồm 3 lane:

1. `maven-verify`
   - chạy `mvn -B -ntp verify`
   - phù hợp để kiểm tra multi-module build hiện tại của repo

2. `deployment-validation`
   - chạy `docker compose config` cho compose gốc và monitoring compose
   - chạy `helm lint` và `helm template` cho chart `ops/deployment/helm/lsf-service`

3. `container-build`
   - build image cho `lsf-example` và `lsf-service-template`
   - mục đích là sanity-check Dockerfile, không push image

### `Release Candidate`

Workflow này chạy bằng `workflow_dispatch` hoặc khi push tag `v*`.

Kết quả đầu ra:

- Maven JAR artifacts
- packaged Helm chart
- `docker save` archives cho `lsf-example` và `lsf-service-template`

Mục đích của workflow này là chứng minh repo có thể đi tới mức release-candidate packaging.
Việc publish thật lên registry được giữ lại như bước sau, vì nó phụ thuộc vào secrets, naming convention, governance và release process của tổ chức sử dụng framework.

## 3. Docker baseline

### Local validation

```powershell
pwsh ./ops/deployment/validate.ps1
```

Script trên:

- validate root `docker-compose.yml`
- validate monitoring compose ở `ops/monitoring`
- lint/render Helm chart

### Build image thủ công

```bash
docker build -f lsf-example/Dockerfile -t lsf-example:local .
docker build -f lsf-service-template/Dockerfile -t lsf-service-template:local .
```

### Chạy local stack bằng Docker Compose

```bash
docker compose --profile apps up --build
```

Stack này sẽ bật:

- Kafka
- Schema Registry
- MySQL
- Redis
- Zipkin
- `lsf-example`
- `template-service`

Nếu cần PostgreSQL để tham khảo outbox runtime tương ứng:

```bash
docker compose --profile postgres up -d postgres
```

## 4. Docker/Compose design notes

- `lsf-example` dùng profile `docker,outbox-mysql`.
- `lsf-service-template` dùng profile `docker` và mặc định trỏ `dependency-service` sang `http://lsf-example:8080`.
- `.dockerignore` loại bỏ `.git`, IDE metadata và `target/` để giảm build context.
- Dockerfile đều là multi-stage build bằng Maven + runtime JRE 21 và chạy bằng non-root user.

## 5. Helm skeleton cho adopter

Chart `ops/deployment/helm/lsf-service` được thiết kế như baseline generic cho một service Spring Boot dùng LSF.

Chart hiện hỗ trợ:

- `Deployment`
- `Service`
- optional `ServiceAccount`
- optional `Ingress`
- optional `HorizontalPodAutoscaler`
- readiness/liveness/startup probes trỏ vào actuator health endpoints
- baseline env vars cho `prometheus`, graceful shutdown, và Spring profile

### Ví dụ values file cho adopter

```yaml
image:
  repository: ghcr.io/acme/customer-service
  tag: 1.0.0

spring:
  profiles: kubernetes,outbox-mysql

env:
  SPRING_APPLICATION_NAME: customer-service
  LSF_KAFKA_BOOTSTRAP_SERVERS: kafka.kafka.svc.cluster.local:9092
  LSF_SCHEMA_REGISTRY_URL: http://schema-registry.kafka.svc.cluster.local:8081
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql.database.svc.cluster.local:3306/customer_service?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
  SPRING_DATASOURCE_USERNAME: customer_service

envFrom:
  secrets:
    - customer-service-secrets
```

### Render hoặc cài chart

```bash
helm template customer-service ops/deployment/helm/lsf-service -f customer-service-values.yaml
helm upgrade --install customer-service ops/deployment/helm/lsf-service -f customer-service-values.yaml
```

## 6. Cách adopter nên dùng các artifact này

1. Bắt đầu từ `lsf-service-template` khi tạo service mới.
2. Copy `Dockerfile` của template và đổi module path trong lệnh Maven nếu module mới đã đổi tên.
3. Chọn đúng profile/outbox runtime theo database thật của service.
4. Dùng chart Helm như skeleton, rồi override image, env, secrets, resources, ingress theo môi trường của tổ chức.
5. Giữ các endpoint admin nội bộ như DLQ/outbox admin phía sau auth hoặc internal network.

## 7. Build/release guidance

- Dùng `CI` workflow cho mọi pull request và nhánh tích hợp.
- Dùng `Release Candidate` workflow để tạo package reviewable trước khi publish.
- Nếu sau này cần publish thật:
  - thêm credentials cho Maven repository hoặc GHCR/registry tương ứng
  - thay bước `upload-artifact` bằng `mvn deploy` và `docker push`
  - tách riêng release governance như changelog, signed tags, hay approval gates nếu tổ chức yêu cầu

## 8. Những gì Phase 6 cố ý chưa làm

- chưa thêm GitOps manifests cho Argo CD/Flux
- chưa publish image thật lên registry
- chưa tự provision Kafka topics, Schema Registry subjects, database, secret stores
- chưa thêm cloud-specific chart cho EKS/GKE/AKS
- chưa thêm Helm chart riêng cho từng module library vì các starter của LSF chủ yếu là library, không phải runtime service độc lập
