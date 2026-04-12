# lsf-saga-starter

## Current evidence and scope

- The strongest current runtime evidence is still `jdbc + direct`.
- Consumer validation in `D:\IdeaProjects\ecommerce-backend` showed that a common hard case is not "general branching", but a local fan-out/fan-in bridge where one order-level saga step waits for many downstream item replies.
- This starter now exposes a small public helper for that pattern:
  - `SagaReplyFanInSession`
  - `SagaReplyFanInSignal`
  - `SagaReplyFanInUpdate`
  - `SagaReplyFanInSupport`
- The helper is intentionally narrow. It helps a consumer aggregate many low-level replies into one order-level decision before advancing a sequential saga. It is not a general workflow graph or branch/join engine.
- If a workflow needs rich parallel joins, dynamic graphs, or cross-step synchronization beyond a local adapter, keep that concern in the consumer for now.

Starter này cung cấp một runtime orchestration/saga dùng được ở mức framework cho các workflow liên service theo event.

## Mục tiêu

- định nghĩa saga theo step có cấu hình rõ ràng
- lưu trạng thái workflow và từng step
- hỗ trợ compensation, timeout, failure transition
- giữ `correlationId`, `causationId`, `requestId` xuyên suốt orchestration flow
- tái sử dụng `EventEnvelope`, `LsfDispatcher` và `OutboxWriter` khi có

## Dependency

```xml
<dependency>
  <groupId>com.myorg.lsf</groupId>
  <artifactId>lsf-saga-starter</artifactId>
  <version>${lsf.version}</version>
</dependency>
```

## Cấu hình cơ bản

```yaml
lsf:
  saga:
    enabled: true
    store: auto          # auto | jdbc | memory
    default-step-timeout: 30s
    observe-dispatch: true
    consume-matching-events: true
    transport:
      mode: auto         # auto | outbox | direct
    timeout-scanner:
      enabled: true
      poll-interval: 5s
      batch-size: 50
    jdbc:
      table: lsf_saga_instance
      initialize-schema: embedded   # never | embedded | always
```

## Định nghĩa một saga

```java
@Bean
SagaDefinition<CheckoutSagaState> checkoutSagaDefinition() {
    return SagaDefinition.<CheckoutSagaState>builder("checkout-orchestrator", CheckoutSagaState.class)
            .step(SagaStep.<CheckoutSagaState>builder("reserveInventory")
                    .command(ctx -> ctx.command(
                            "inventory-commands",
                            ctx.state().orderId(),
                            "inventory.reserve.requested.v1",
                            new ReserveInventoryCommand(ctx.state().orderId(), ctx.state().items())
                    ))
                    .onReply("inventory.reserved.v1", InventoryReservedReply.class,
                            (ctx, env, payload) -> SagaReplyDecision.success(
                                    ctx.state().withReservation(payload.reservationId())
                            ))
                    .onReply("inventory.reserve.failed.v1", InventoryFailedReply.class,
                            (ctx, env, payload) -> SagaReplyDecision.failure(
                                    ctx.state().withFailure(payload.reason()),
                                    payload.reason()
                            ))
                    .compensation(comp -> comp
                            .command(ctx -> ctx.command(
                                    "inventory-commands",
                                    ctx.state().orderId(),
                                    "inventory.release.requested.v1",
                                    new ReleaseInventoryCommand(ctx.state().orderId(), ctx.state().reservationId())
                            ))
                            .onReply("inventory.released.v1", InventoryReleasedReply.class,
                                    (ctx, env, payload) -> SagaReplyDecision.success(ctx.state()))
                    )
                    .timeout(Duration.ofSeconds(30))
                    .failureMode(SagaFailureMode.COMPENSATE)
                    .build())
            .step(SagaStep.<CheckoutSagaState>builder("chargePayment")
                    .command(ctx -> ctx.command(
                            "payment-commands",
                            ctx.state().orderId(),
                            "payment.charge.requested.v1",
                            new ChargePaymentCommand(ctx.state().orderId(), ctx.state().totalAmount())
                    ))
                    .onReply("payment.charged.v1", PaymentChargedReply.class,
                            (ctx, env, payload) -> SagaReplyDecision.success(
                                    ctx.state().withPayment(payload.paymentId())
                            ))
                    .onReply("payment.charge.failed.v1", PaymentFailedReply.class,
                            (ctx, env, payload) -> SagaReplyDecision.failure(
                                    ctx.state().withFailure(payload.reason()),
                                    payload.reason()
                            ))
                    .timeout(Duration.ofSeconds(45))
                    .failureMode(SagaFailureMode.COMPENSATE)
                    .build())
            .build();
}
```

## Khởi động orchestration

```java
@RequiredArgsConstructor
@Service
public class CheckoutWorkflowService {

    private final LsfSagaOrchestrator orchestrator;

    public SagaInstance start(CheckoutRequest request, String correlationId, String requestId) {
        CheckoutSagaState initialState = new CheckoutSagaState(
                request.orderId(),
                request.items(),
                request.totalAmount(),
                null,
                null,
                null
        );

        return orchestrator.start(
                "checkout-orchestrator",
                request.orderId(),
                initialState,
                SagaStartOptions.builder()
                        .correlationId(correlationId)
                        .requestId(requestId)
                        .build()
        );
    }
}
```

## Cách orchestration nhận reply event

Nếu service orchestration đã dùng `lsf-eventing-starter`, starter này có thể bọc `LsfDispatcher` để tự consume những reply event khớp `correlationId` của saga đang chờ.

Nghĩa là:

- command được publish ra topic dưới dạng `EventEnvelope`
- downstream service xử lý và trả reply event
- orchestration service nhận lại reply event
- `lsf-saga-starter` tự advance workflow/compensation mà không cần viết handler business giả chỉ để route state machine

Ví dụ service downstream có thể phát reply event như sau:

```java
publisher.publish(
        "payment-events",
        payment.orderId(),
        "payment.charged.v1",
        payment.orderId(),
        new PaymentChargedReply(payment.orderId(), payment.paymentId()),
        LsfPublishOptions.builder()
                .correlationId(commandEnvelope.getCorrelationId())
                .causationId(commandEnvelope.getEventId())
                .requestId(commandEnvelope.getRequestId())
                .build()
);
```

## Ghi chú thiết kế

- `transport.mode=outbox` là lựa chọn khuyến nghị cho orchestration bền vững hơn.
- `memory` store phù hợp cho local/dev/test; `jdbc` phù hợp hơn cho multi-instance orchestration.
- Runtime hiện ưu tiên sequential orchestration và compensation tuyến tính để giữ abstraction rõ ràng, dễ kiểm chứng và không drift sang workflow engine quá tổng quát.

## Validation hiện có

- `LsfSagaRuntimeIntegrationTest` chạy trong lane mặc định, dùng `direct transport` + JDBC store + `EmbeddedKafka`
- suite này verify:
  - start saga
  - dispatch sequential step command
  - consume reply event khớp `correlationId`
  - propagate `correlationId`, `causationId`, `requestId`
  - transition sang `COMPLETED` ở success path
  - transition sang `COMPENSATED` ở non-happy-path tối thiểu
- `LsfSagaAutoConfigurationTest` cũng khóa fail-fast behavior và regression cho trường hợp `lsf-kafka-starter` cung cấp `KafkaTemplate`

Lệnh chạy nhanh:

```bash
mvn -B -ntp -pl lsf-saga-starter -am test
```

Giới hạn hiện tại vẫn cần nêu rõ:

- coverage mới tập trung vào sequential orchestration path
- integration hiện verify `direct transport`; `outbox transport` vẫn là follow-up riêng
- starter này chưa nên được diễn giải như workflow engine tổng quát cho branch/parallel join phức tạp
