# Contract: Merchant Order Mutations & Manual Settlement (US3, US4, US5)

## Merchant price override on unpaid order line (US3)

**Endpoint**: `PATCH /v1/merchant/orders/{orderId}/items/{itemId}/price`

**Request**:
```json
{ "unitPriceMinor": 900 }
```

**Response `200 OK`** — full `OrderResponse` with the overridden line:
```json
{
  "orderId": "ORD-DEFERRED00001",
  "status": "CREATED",
  "items": [
    {
      "productId": "22222222-2222-2222-2222-222222222221",
      "name": "Coffee",
      "quantity": 2,
      "unitPriceMinor": 900,
      "lineTotalMinor": 1800
    }
  ],
  "totalAmountMinor": 1800,
  ...
}
```

**Notes**:
- `unitPriceMinor` in response reflects the override value (900), not catalog price (1250).
- `totalAmountMinor` is recalculated from effective line totals.
- Override is per-line (per `itemId`), not per product across all lines.

**Error cases**:
- `409 Conflict` when `status != CREATED`
- `400 Bad Request` when `unitPriceMinor < 1`
- `404 Not Found` when order or item not found

---

## Merchant list branch orders — CREATED vs FAILED distinction (US5, FR-021)

**Endpoint**: `GET /v1/merchant/orders?branchId={branchId}`

**Response `200 OK`** — array of `OrderResponse`:
```json
[
  {
    "orderId": "ORD-DEFERRED00001",
    "usesDeferredPayment": true,
    "status": "CREATED",
    "paymentSource": null,
    "paymentRetryAllowed": true,
    ...
  },
  {
    "orderId": "ORD-PAYNOW-FAIL1",
    "usesDeferredPayment": false,
    "status": "FAILED",
    "paymentSource": null,
    "paymentRetryAllowed": true,
    ...
  },
  {
    "orderId": "ORD-SETTLED00001",
    "usesDeferredPayment": true,
    "status": "PAID",
    "paymentSource": "MANUAL_SETTLEMENT",
    "paymentRetryAllowed": false,
    ...
  }
]
```

**Key**: `status=CREATED` = awaiting first payment; `status=FAILED` = payment attempted and failed. Both have `paymentRetryAllowed=true`. `paymentSource` appears only on `PAID` orders.

---

## Manual settlement (US4)

**Endpoint**: `POST /v1/merchant/orders/{orderId}/settle`

**Request** (note is optional):
```json
{ "note": "Customer paid cash at counter" }
```

Or with no note:
```json
{}
```

**Response `200 OK`** — full `OrderResponse`:
```json
{
  "orderId": "ORD-DEFERRED00001",
  "status": "PAID",
  "paymentSource": "MANUAL_SETTLEMENT",
  "paidAt": "2026-05-15T14:00:00Z",
  "paymentRetryAllowed": false,
  "usesDeferredPayment": true,
  ...
}
```

**Eligible starting statuses**: `CREATED` (deferred or failed-pay-now) or `FAILED` (FR-015, FR-016).

**Error cases**:
- `409 Conflict` when order is already `PAID` (duplicate settlement, FR-019)
- `409 Conflict` when order is `CANCELLED`
- `404 Not Found` when order not found

---

## Merchant get order by orderId (US5)

**Endpoint**: `GET /v1/merchant/orders/{orderId}`

New endpoint (not currently in codebase). Returns `OrderResponse` for a specific order by orderId string, scoped to the authenticated merchant.

**Response `200 OK`**: Same `OrderResponse` shape as shopper GET, plus existing merchant fields.

**Error cases**:
- `404 Not Found` when orderId not found
- `403 Forbidden` if orderId belongs to different merchant (implementation detail)

---

## Contract test coverage required

| Test | Endpoint | Scenario |
|------|----------|---------|
| merchant price override | `PATCH /v1/merchant/orders/{orderId}/items/{itemId}/price` | Override updates line total and order total |
| merchant price override rejected | same | 409 when status ≠ CREATED |
| manual settle deferred order | `POST /v1/merchant/orders/{orderId}/settle` | CREATED → PAID, paymentSource=MANUAL_SETTLEMENT |
| manual settle failed order | same | FAILED → PAID, paymentSource=MANUAL_SETTLEMENT |
| manual settle already paid | same | 409 Conflict |
| merchant list orders distinguishes statuses | `GET /v1/merchant/orders` | CREATED vs FAILED vs PAID in same list |
| merchant get order by orderId | `GET /v1/merchant/orders/{orderId}` | Returns full OrderResponse |
