# Phase 1 — Data Model: Push Notifications for Payment Status

**Feature**: `006-push-notifications` · **Date**: 2026-04-15

All tables live in the default schema and are created via Hibernate `ddl-auto: update` (project convention — no Liquibase/Flyway). Names below are the logical JPA entity names; physical names follow the existing snake_case convention.

## Entity: `PaymentStatus` (authoritative current state)

Read model consumed by `GET /v1/payments/{paymentRequestId}` and by the push dispatcher.

| Field                | Type                     | Notes                                                                 |
|----------------------|--------------------------|-----------------------------------------------------------------------|
| `paymentRequestId`   | `UUID` / `String` (PK)   | Matches the identifier minted when the QR payment request is created. |
| `currentStatus`      | `enum PaymentStatusCode` | `PENDING`, `EXECUTED`, `SETTLED`, `FAILED`, `EXPIRED`.                |
| `lastEventId`        | `String` (nullable)      | TrueLayer event id that produced the current status.                  |
| `lastEventAt`        | `Instant`                | Event timestamp (from webhook payload).                                |
| `updatedAt`          | `Instant`                | Server-side write time (auditing).                                     |
| `version`            | `Long` (`@Version`)      | Optimistic locking to serialize out-of-order updates.                  |

**Validation / invariants**:
- `currentStatus` transitions are monotonic on the lattice `PENDING → EXECUTED → SETTLED` and `PENDING → FAILED | EXPIRED`. Attempts to regress are rejected at service level (return current row unchanged).
- `lastEventAt` is non-decreasing within a row; if an incoming event is older, the update is ignored (still idempotent).
- Row is created on first observed event for a `paymentRequestId`; payment-request creation itself does **not** create the row (avoids coupling modules).

**Indexes**: PK on `paymentRequestId`. No other indexes for P1 — all queries are by PK.

---

## Entity: `DeviceRegistration`

One row per (paymentRequestId, expoPushToken) pairing. `expoPushToken` is globally unique across rows.

| Field                | Type                          | Notes                                                           |
|----------------------|-------------------------------|-----------------------------------------------------------------|
| `id`                 | `UUID` (PK)                   | Surrogate key.                                                  |
| `paymentRequestId`   | `UUID` / `String` (indexed)   | Foreign relationship by value (no FK constraint needed).        |
| `payerRef`           | `String` (nullable, indexed)  | Reserved for later: attach tokens to a payer identity.           |
| `expoPushToken`      | `String` (UNIQUE)             | Opaque token `ExponentPushToken[...]`.                          |
| `platform`           | `enum DevicePlatform`         | `IOS`, `ANDROID` — client-reported, optional for routing.        |
| `createdAt`          | `Instant`                     | First registration time.                                        |
| `lastSeenAt`         | `Instant`                     | Refreshed on each re-registration.                              |
| `invalid`            | `boolean`                     | Set `true` when Expo receipts return `DeviceNotRegistered`.     |

**Validation / invariants**:
- `UNIQUE(expoPushToken)` — re-registering the same token is an idempotent upsert that refreshes `lastSeenAt` and associates the (possibly new) `paymentRequestId`.
- A token marked `invalid` is excluded from dispatch queries.
- Registration requires at least one of `paymentRequestId` or `payerRef` to be non-null.

**Indexes**: PK on `id`; UNIQUE on `expoPushToken`; non-unique on `paymentRequestId` (hot read path — fan-out query); non-unique on `payerRef` (reserved).

**Retention**: rows where the associated `paymentRequestId` has reached a terminal status for > 30 days are eligible for deletion by a later cleanup job (out of scope for this slice).

---

## Entity: `ProcessedWebhookEvent` (idempotency ledger)

Small table whose only job is to dedupe TrueLayer webhook redeliveries.

| Field         | Type        | Notes                                       |
|---------------|-------------|---------------------------------------------|
| `eventId`     | `String` PK | TrueLayer-provided event identifier.        |
| `processedAt` | `Instant`   | Server write time.                          |

**Invariants**: `INSERT ... ON CONFLICT DO NOTHING` (or equivalent catch of unique-violation) in the webhook pipeline; a conflict short-circuits the handler before any downstream work.

**Indexes**: PK only.

---

## Entity: `PushDeliveryAttempt` *(optional, observability)*

Included for FR-011. Can be demoted to logs-only if ops prefers; carrying it here keeps the option open.

| Field                | Type                         | Notes                                           |
|----------------------|------------------------------|-------------------------------------------------|
| `id`                 | `UUID` PK                    |                                                 |
| `paymentRequestId`   | `UUID` / `String` (indexed)  |                                                 |
| `expoPushToken`      | `String` (indexed)           | FK-by-value to `DeviceRegistration`.            |
| `statusCode`         | `enum PaymentStatusCode`     | Which transition was pushed.                    |
| `ticketId`           | `String` (nullable)          | From Expo ticket response.                      |
| `outcome`            | `enum DeliveryOutcome`       | `ACCEPTED`, `REJECTED`, `RETRIED`, `GIVEN_UP`.  |
| `receiptOutcome`     | `enum DeliveryOutcome`       | Populated after receipt poll.                   |
| `errorCode`          | `String` (nullable)          | e.g., `DeviceNotRegistered`.                    |
| `createdAt`          | `Instant`                    |                                                 |
| `updatedAt`          | `Instant`                    |                                                 |

**Retention**: 30 days; pruned by the same cleanup job as `DeviceRegistration`.

---

## Domain event: `PaymentStatusChanged`

Internal Spring Modulith event (not persisted to any of the tables above; Modulith's event-publication store handles delivery reliability).

```kotlin
data class PaymentStatusChanged(
    val paymentRequestId: String,
    val newStatus: PaymentStatusCode,
    val previousStatus: PaymentStatusCode?,
    val occurredAt: Instant,
    val sourceEventId: String,   // TrueLayer event id
)
```

- Published by `WebhookController` (or a dedicated service it delegates to) **after** the `PaymentStatus` upsert commits.
- Consumed by `payments.push.PaymentStatusChangedListener` via `@ApplicationModuleListener` (after-commit, persistent).
- Also consumable by any future module (e.g., analytics) without changes here.

## State transitions

```text
          ┌─────────┐      ┌──────────┐      ┌──────────┐
          │ PENDING │ ───▶ │ EXECUTED │ ───▶ │ SETTLED  │
          └─────────┘      └──────────┘      └──────────┘
               │                 │
               ▼                 ▼
          ┌─────────┐      ┌──────────┐
          │ FAILED  │      │ FAILED   │
          └─────────┘      └──────────┘
               │
               ▼
          ┌─────────┐
          │ EXPIRED │
          └─────────┘
```

Terminal statuses: `SETTLED`, `FAILED`, `EXPIRED`. Once terminal, `PaymentStatus.currentStatus` is immutable.

## Mapping to spec entities

| Spec entity              | Physical representation                          |
|--------------------------|--------------------------------------------------|
| Payment Request          | Existing, unchanged (owned by `payments.qrpay`). |
| Payment Status Event     | `ProcessedWebhookEvent` (dedup) + `PaymentStatus` (current) + `PaymentStatusChanged` (transport). |
| Device Registration      | `DeviceRegistration`.                            |
| Push Delivery Attempt    | `PushDeliveryAttempt`.                           |
