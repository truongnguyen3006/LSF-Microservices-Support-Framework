# lsf-outbox-admin-starter

REST API để vận hành outbox: list, requeue, mark-failed, (optional) delete.

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-admin-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Enable

```yaml
lsf:
  outbox:
    admin:
      enabled: true
```

## Safety

- DELETE mặc định bị chặn. Muốn cho phép phải bật:

```yaml
lsf:
  outbox:
    admin:
      allow-delete: true
```

> Nên bảo vệ endpoints bằng Spring Security trong môi trường thật.
