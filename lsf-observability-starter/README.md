# lsf-observability-starter

Bổ sung observability cho event dispatch: MDC + metrics + latency timer.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-observability-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Những gì bạn nhận được

- MDC fields (ví dụ): eventId, eventType, correlationId... để log dễ trace
- Metrics cho outcomes: success/fail/duplicate + timer xử lý
