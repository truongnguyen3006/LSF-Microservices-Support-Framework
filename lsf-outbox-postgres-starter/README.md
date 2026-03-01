# lsf-outbox-postgres-starter

Outbox pattern cho Postgres: writer + publisher poller (lease/skip locked).

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-outbox-postgres-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Config

```yaml
lsf:
  outbox:
    enabled: true
    table: public.lsf_outbox
    publisher:
      enabled: true
      claim-strategy: SKIP_LOCKED
```

`lsf.outbox.table` được validate theo format an toàn.
