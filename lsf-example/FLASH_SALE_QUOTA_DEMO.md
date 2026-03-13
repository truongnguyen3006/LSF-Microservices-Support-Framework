# Flash Sale Quota Demo

This example turns the generic `reserve / confirm / release` API into a simple business flow that looks like a real ecommerce flash-sale reservation.

## Endpoints

- `POST /demo/flash-sale/orders/reserve`
- `POST /demo/flash-sale/orders/{orderId}/confirm`
- `POST /demo/flash-sale/orders/{orderId}/release`
- `GET /demo/flash-sale/orders/{orderId}`

## Request example

```json
{
  "tenant": "shopA",
  "sku": "IPHONE15-128-BLACK",
  "quantity": 1,
  "requestId": "fs-order-001"
}
```

## How it maps to quota

- `tenant + sku` -> `quotaKey = shopA:flashsale_sku:IPHONE15-128-BLACK`
- `requestId` -> unique reservation identity
- `reserve` -> hold inventory temporarily
- `confirm` -> convert hold into confirmed usage
- `release` -> return reserved/confirmed inventory depending on quota config

## Why this matters

This demo is closer to the real ecommerce integration than the raw quota endpoints because it shows how the framework can sit behind a business use case:

- flash sale inventory hold
- avoid overselling
- deduplicate by requestId
- keep a simple order snapshot for UI/demo purposes

## Suggested demo flow

1. Reserve `shopA + IPHONE15-128-BLACK` several times until the quota is exhausted.
2. Show that later reservations become `REJECTED`.
3. Confirm one existing order.
4. Release another existing order.
5. Query `GET /demo/flash-sale/orders/{orderId}` to show the current state.
