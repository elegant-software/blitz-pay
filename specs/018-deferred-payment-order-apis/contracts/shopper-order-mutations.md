# Contract: Shopper Order Mutations (US2, US3)

## Create order — Pay later (US2)

**Endpoint**: `POST /v1/orders`

**Request**:
```json
{
  "branchId": "33333333-3333-3333-3333-333333333333",
  "orderType": "PRE_ORDER",
  "usesDeferredPayment": true,
  "customerLocation": null,
  "items": [
    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 1 }
  ],
  "paymentMethod": "TRUELAYER"
}
```

**Response `201 Created`**:
```json
{
  "orderId": "ORD-DEFERRED00001",
  "merchantId": "11111111-1111-1111-1111-111111111111",
  "branchId": "33333333-3333-3333-3333-333333333333",
  "orderType": "PRE_ORDER",
  "usesDeferredPayment": true,
  "status": "CREATED",
  "creatorType": "SHOPPER",
  "createdById": "shopper-xyz",
  "currency": "EUR",
  "totalAmountMinor": 1250,
  "paymentRetryAllowed": true,
  "paymentSource": null,
  "paymentReference": null,
  "items": [
    {
      "productId": "22222222-2222-2222-2222-222222222221",
      "name": "Coffee",
      "quantity": 1,
      "unitPriceMinor": 1250,
      "lineTotalMinor": 1250
    }
  ],
  "createdAt": "2026-05-15T10:00:00Z"
}
```

**Key assertions**:
- `status == CREATED`
- `paymentReference` absent/null (no payment started)
- `usesDeferredPayment == true`
- `paymentSource == null`

---

## Add item to unpaid order (US3)

**Endpoint**: `POST /v1/orders/{orderId}/items`

**Request**:
```json
{ "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2 }
```

**Response `200 OK`** — full `OrderResponse` with updated items and `totalAmountMinor`.

**Error cases**:
- `409 Conflict` when `status != CREATED`
- `404 Not Found` when order not found
- `400 Bad Request` when `quantity < 1`

---

## Update item quantity on unpaid order (US3)

**Endpoint**: `PATCH /v1/orders/{orderId}/items/{itemId}`

**Request**:
```json
{ "quantity": 3 }
```

**Response `200 OK`** — full `OrderResponse` with updated `lineTotalMinor` and `totalAmountMinor`.

**Error cases**:
- `409 Conflict` when `status != CREATED`
- `404 Not Found` when order or item not found
- `400 Bad Request` when `quantity < 1`

---

## Remove item from unpaid order (US3)

**Endpoint**: `DELETE /v1/orders/{orderId}/items/{itemId}`

**Response `200 OK`** — full `OrderResponse`:
- If items remain: `status == CREATED`, updated totals.
- If last item removed: `status == CANCELLED`, `totalAmountMinor == 0`.

**Error cases**:
- `409 Conflict` when `status != CREATED`
- `404 Not Found` when order or item not found

---

## Cancel unpaid order (US3)

**Endpoint**: `POST /v1/orders/{orderId}/cancel`

**Request**: Empty body

**Response `200 OK`**:
```json
{
  "orderId": "ORD-DEFERRED00001",
  "status": "CANCELLED",
  "paymentRetryAllowed": true,
  ...
}
```

**Error cases**:
- `409 Conflict` when `status != CREATED`
- `404 Not Found`

---

## Shopper list orders — checkoutIntent visible (US5)

**Endpoint**: `GET /v1/orders`

**Response `200 OK`** — array of `OrderSummaryResponse`:
```json
[
  {
    "orderId": "ORD-DEFERRED00001",
    "usesDeferredPayment": true,
    "status": "CREATED",
    "paymentSource": null,
    "paymentRetryAllowed": true,
    ...
  }
]
```

---

## Get single order — paymentSource visible on paid order (US5)

**Endpoint**: `GET /v1/orders/{orderId}`

**Response for manually settled order**:
```json
{
  "orderId": "ORD-DEFERRED00001",
  "status": "PAID",
  "paymentSource": "MANUAL_SETTLEMENT",
  "paidAt": "2026-05-15T14:00:00Z",
  "paymentRetryAllowed": false,
  ...
}
```

**Response for app-paid order**:
```json
{
  "orderId": "ORD-PAYNOW00001",
  "status": "PAID",
  "paymentSource": "APP_PAYMENT",
  "paidAt": "2026-05-15T11:00:00Z",
  "paymentRetryAllowed": false,
  ...
}
```
