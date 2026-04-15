# BlitzPay – Mobile-facing REST API Reference

**Base URL:** `https://<host>/v1`  
All endpoints use URL-path versioning (`/v1/...`). Default version is `1`.

---

## 1. Create a payment request

**`POST /v1/payments/request`**

Initiates a TrueLayer payment and returns the IDs needed to open the bank redirect.

**Request body**
```json
{
  "orderId": "order-123",
  "amountMinorUnits": 1999,
  "currency": "GBP",
  "userDisplayName": "Alice Smith",
  "redirectReturnUri": "myapp://payment-return"
}
```

**Response `202 Accepted`**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "paymentId": "tl-payment-id",
  "resourceToken": "tl-resource-token",
  "redirectReturnUri": "https://payment.truelayer.com/..."
}
```

---

## 2. Stream payment status (SSE)

**`GET /v1/qr-payments/{paymentRequestId}/events`**  
`Accept: text/event-stream`

Opens a Server-Sent Events stream that pushes `PaymentResult` updates for the given `paymentRequestId`. Auto-closes after 5 minutes of inactivity.

**SSE event shape**
```
event: payment
id: <paymentRequestId>
data: { "paymentRequestId": "...", "paymentId": "...", "status": "...", ... }
```

---

## 3. Register device for push notifications

**`POST /v1/devices`**

Registers (or refreshes) an Expo push token so the device receives payment status push notifications.

**Request body**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
  "platform": "IOS"
}
```

| Field | Required | Notes |
|---|---|---|
| `paymentRequestId` | yes | UUID of an existing payment request |
| `expoPushToken` | yes | Must match `ExponentPushToken[...]` |
| `platform` | no | `IOS` or `ANDROID` |

**Response `201 Created`** (new) or **`200 OK`** (refresh)
```json
{
  "id": "uuid",
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "expoPushToken": "ExponentPushToken[...]",
  "platform": "IOS"
}
```

**Errors**
- `404 Not Found` — `paymentRequestId` does not exist

---

## 4. Unregister device

**`DELETE /v1/devices/{expoPushToken}`**

Removes the push token. Call on logout or in response to a user privacy request.

**Response `204 No Content`**

---

## 5. Fetch payment status (fallback poll)

**`GET /v1/payments/{paymentRequestId}`**

Returns the authoritative current status stored server-side. Use as a fallback when SSE or push is unavailable.

**Response `200 OK`**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SETTLED",
  "terminal": true,
  "lastEventAt": "2026-04-15T14:23:00Z"
}
```

**Status lifecycle**

```
PENDING → EXECUTED → SETTLED (terminal)
                   → FAILED  (terminal)
                   → EXPIRED (terminal)
```

`terminal: true` means no further status changes will occur.

**Errors**
- `400 Bad Request` — `paymentRequestId` is not a valid UUID
- `404 Not Found` — no status record yet (payment too new or unknown)

---

## 6. Ingest mobile logs

**`POST /v1/observability/mobile-logs`**

Sends batched log events from the app; the server forwards them to the OTLP/Loki pipeline.

**Request body**
```json
{
  "context": {
    "serviceName": "blitzpay-mobile",
    "serviceVersion": "1.2.0",
    "environment": "production",
    "sessionId": "sess-abc",
    "osName": "iOS",
    "osVersion": "17.4"
  },
  "events": [
    {
      "timestamp": "2026-04-15T14:23:00.000Z",
      "severityText": "ERROR",
      "message": "Payment flow failed",
      "attributes": { "screen": "PaymentScreen" },
      "exception": {
        "type": "NetworkError",
        "message": "timeout",
        "stack": "...",
        "isFatal": false
      }
    }
  ]
}
```

`context` is optional. Each event only requires `message`. All other fields are optional.

**Response `202 Accepted`**
```json
{ "accepted": 1 }
```

---

## Push notification payload (Expo)

When a payment status changes, the server sends an Expo push notification to all registered tokens for that `paymentRequestId`. The mobile app should handle the notification and either display it directly or trigger a poll to `/v1/payments/{paymentRequestId}` for the latest state.

The push is triggered for every status transition: `PENDING` → `EXECUTED` → `SETTLED` / `FAILED` / `EXPIRED`.

---

## Source files

| Endpoint | Controller |
|---|---|
| `POST /v1/payments/request` | `payments/qrpay/PaymentRequestController.kt` |
| `GET /v1/qr-payments/{id}/events` | `payments/qrpay/QrPaymentSseController.kt` |
| `POST /v1/devices` | `payments/push/api/DeviceRegistrationController.kt` |
| `DELETE /v1/devices/{token}` | `payments/push/api/DeviceRegistrationController.kt` |
| `GET /v1/payments/{id}` | `payments/push/api/PaymentStatusController.kt` |
| `POST /v1/observability/mobile-logs` | `mobileobservability/MobileLogsController.kt` |
