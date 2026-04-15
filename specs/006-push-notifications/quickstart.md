# Quickstart — Push Notifications for Payment Status

**Feature**: `006-push-notifications` · **Date**: 2026-04-15

This quickstart shows how to exercise the feature end-to-end once implemented, both locally and in tests. It is intentionally short — it is not the full implementation guide.

## Prerequisites

- The standard env vars listed in `CLAUDE.md` (TrueLayer credentials) for `bootRun`.
- One new env var: `EXPO_ACCESS_TOKEN` — access token for your Expo project. Not required for contract tests.
- A running PostgreSQL 16 (`ddl-auto: update` creates the new tables on startup).

## Local run

```bash
export EXPO_ACCESS_TOKEN=...   # in addition to the existing TRUELAYER_* vars
./gradlew bootRun
```

### 1. Create a payment request (unchanged)

```bash
curl -sS -X POST http://localhost:8080/v1/payments/requests \
  -H 'Content-Type: application/json' \
  -d '{"amountMinor": 1000, "currency": "GBP"}' | jq .
# → { "paymentRequestId": "7f1a8e22-...", "qrPayload": "..." }
```

### 2. Register the payer's device

```bash
curl -sS -X POST http://localhost:8080/v1/devices \
  -H 'Content-Type: application/json' \
  -d '{
        "paymentRequestId": "7f1a8e22-...",
        "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxx]",
        "platform": "IOS"
      }' | jq .
```

Repeating the call with the same token is a no-op that refreshes `lastSeenAt`.

### 3. Simulate the TrueLayer webhook

```bash
# Payload + signature headers per existing TrueLayer signing rules — use the
# repo's existing WebhookController test helpers.
curl -sS -X POST http://localhost:8080/payments/webhooks/truelayer \
  -H 'Content-Type: application/json' \
  -H 'Tl-Signature: <...>' \
  --data-binary @sample-payload-executed.json
```

Expected:
1. Webhook verified, `ProcessedWebhookEvent` row inserted (dedup).
2. `PaymentStatus` upserted to `EXECUTED`.
3. `PaymentStatusChanged` event published after commit.
4. `payments.push` listener picks it up, queries `DeviceRegistration` by `paymentRequestId`, POSTs an Expo push batch, and records `PushDeliveryAttempt` rows.
5. The device receives a push notification within seconds.

### 4. Status fallback

```bash
curl -sS http://localhost:8080/v1/payments/7f1a8e22-... | jq .
# → { "paymentRequestId": "...", "status": "EXECUTED", "terminal": false, "lastEventAt": "..." }
```

The mobile client calls this on foreground, on each payment screen mount, and on any "stuck" state.

## Contract tests (no TrueLayer, no Expo, no DB)

```bash
./gradlew contractTest
```

Covers:
- `GET /v1/payments/{id}` — 200 for known, 404 for unknown/unauthorized, 400 for malformed.
- `POST /v1/devices` — 201 on new, 200 on refresh, 400 on malformed token, 404 on unknown payment.
- `DELETE /v1/devices/{token}` — 204 (including when token unknown).

Under the `contract-test` profile, `ExpoPushClient` is replaced with a no-op mock.

## Unit tests

```bash
./gradlew test
```

Focus areas:
- `PaymentStatusServiceTest` — monotonic status transitions, out-of-order ignore.
- `PaymentStatusChangedListenerTest` — fan-out to all valid tokens, skip invalid.
- `ExpoPushClientTest` — batches up to 100, maps Expo error codes to delivery outcomes.
- `DeviceRegistrationServiceTest` — idempotent upsert on duplicate token.

## Removing the SSE endpoint (final slice)

Once the mobile client has rolled out and telemetry shows no active SSE consumers for the relevant observation window:

```bash
# Delete these files, then rebuild + contract test:
src/main/kotlin/.../payments/qrpay/QrPaymentSseController.kt
src/main/kotlin/.../payments/support/PaymentUpdateBus.kt
# and any references from PaymentInitRequestListener / PaymentRequestController
```

This step is scheduled as the last task in `tasks.md` and lands as its own commit (`refactor: remove obsolete QR SSE stream`).
