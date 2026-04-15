# Phase 0 — Research: Push Notifications for Payment Status

**Feature**: `006-push-notifications` · **Date**: 2026-04-15

This document resolves the open questions that came out of the Technical Context in `plan.md`. Nothing below introduces a `NEEDS CLARIFICATION` — every item has a concrete decision with rationale.

## R1. Push delivery channel

- **Decision**: Use Expo Push Service (`https://exp.host/--/api/v2/push/send`) as the single outbound channel. Call it from Kotlin via Spring `WebClient` — no SDK dependency.
- **Rationale**: The spec names Expo Push explicitly. Expo multiplexes to APNs and FCM, so the server carries no platform-specific credentials. The wire format is stable JSON; a thin client is ~100 lines and avoids dragging a Node/Expo SDK into a JVM service. Using `WebClient` fits the existing reactive stack (`spring-boot-starter-webflux` already on classpath).
- **Alternatives considered**:
  - Direct APNs + FCM integration: two credential sets, two client libraries, platform-specific payload handling. Rejected on cost/complexity for a single Expo-managed mobile client.
  - Firebase Admin SDK as a cross-platform fan-out: feasible, but pulls in a heavy dependency and re-introduces per-platform concerns. Rejected.

## R2. Expo authentication

- **Decision**: Authenticate to Expo using an **access token** configured as env var `EXPO_ACCESS_TOKEN` (bound via `ExpoPushProperties`). Pass as `Authorization: Bearer <token>`.
- **Rationale**: Expo supports both unauthenticated and access-token-authenticated pushes; authenticated pushes are required for enhanced security features (receipt-based invalidation signals, rate-limit headroom). A single env var matches the existing TrueLayer-style configuration in this repo.
- **Alternatives considered**: Unauthenticated push — rejected because we want per-project rate-limit headroom and receipts.

## R3. Where webhook-to-push handoff lives

- **Decision**: `WebhookController` (in `payments.truelayer.inbound`) publishes a new domain event `PaymentStatusChanged(paymentRequestId, status, occurredAt, eventId)` via `ApplicationEventPublisher` after it persists the status transition. A listener in the new `payments.push` module (`@ApplicationModuleListener`) consumes that event and triggers dispatch.
- **Rationale**: Matches the existing Modulith convention (`CLAUDE.md`: "Modules communicate via Spring ApplicationEventPublisher / @ApplicationModuleListener. Direct bean coupling across modules is an architecture smell"). `@ApplicationModuleListener` provides transactional-after-commit semantics and Modulith event persistence, which gives us crash-safe at-least-once delivery of the internal event without having to engineer it.
- **Alternatives considered**:
  - Have `WebhookController` call Expo directly: violates module boundaries and couples webhook ACK latency to Expo availability. Rejected.
  - Use the existing in-memory `PaymentUpdateBus`: non-persistent, single-JVM, and being removed anyway. Rejected.

## R4. Authoritative status storage

- **Decision**: Introduce a `payment_status` table keyed by `payment_request_id` with columns `{ current_status, last_event_id, last_event_at, updated_at }`. Updates are upserts guarded by a monotonic status-ordering check (don't move settled → executed).
- **Rationale**: The spec's P1 fallback endpoint needs the answer in constant time without replaying events. Current code only broadcasts via an in-memory bus; nothing is persisted. A read-model table is the minimum necessary addition; no event-sourcing framework warranted at this scale.
- **Alternatives considered**:
  - Derive current status on-demand from TrueLayer API: adds an outbound dependency and latency to every status query. Rejected.
  - Use Modulith event-publication store as the source of truth: it's for reliability, not querying. Rejected.

## R5. Idempotency for webhooks

- **Decision**: Persist each processed TrueLayer webhook `event_id` in a narrow `processed_webhook_event` table (or reuse Modulith's completed-events table) — on duplicate receipt, short-circuit before publishing `PaymentStatusChanged`. Status updates additionally enforce monotonic transitions.
- **Rationale**: TrueLayer explicitly may redeliver; at-least-once is the standard webhook guarantee. We need dedup at ingest and a second line of defense at the status-update step.

## R6. Device registration model

- **Decision**: `device_registration` table with `{ id, payment_request_id (nullable), payer_ref (nullable), expo_push_token (unique), platform, created_at, last_seen_at, invalid (boolean) }`. Scope for P1 is per-payment-request registration; `payer_ref` is reserved so a later slice can attach tokens to a broader payer identity without schema churn.
- **Rationale**: Spec Assumption: "Device registration is tied at minimum to a payment request identifier; broader 'payer identity' registration is acceptable but not required for the P1 slice." A `UNIQUE(expo_push_token)` keeps registration idempotent; `invalid` lets us retire tokens on Expo receipt `DeviceNotRegistered` without deleting audit data.
- **Alternatives considered**:
  - One token per row, many rows per device: simpler but loses idempotency guarantees. Rejected.
  - Store the token only in memory keyed by payment request: doesn't survive restarts; trivially breaks reliability SC. Rejected.

## R7. Expo receipts handling

- **Decision**: Send pushes synchronously (per HTTP call), log the immediate ticket response, and schedule a single receipt-check job ~15 minutes after send per Expo's guidance. Mark tokens invalid on `DeviceNotRegistered` / `InvalidCredentials` error codes returned in receipts.
- **Rationale**: Expo returns tickets immediately but the *real* delivery outcome is only available via receipts. This is the mechanism the spec's FR-007 depends on (retire tokens reported invalid by the provider).
- **Alternatives considered**:
  - Skip receipts, trust tickets: fails FR-007. Rejected.
  - Immediate receipt polling: wastes requests; Expo explicitly recommends a 15-minute delay. Rejected.

## R8. Authorization for `GET /v1/payments/{paymentRequestId}`

- **Decision**: For P1, authorize by possession of the `paymentRequestId` (treated as a capability — same implicit model as today's SSE endpoint and QR code payload). Return **404 Not Found** for both "doesn't exist" and "exists but caller not entitled" to prevent existence leaks.
- **Rationale**: The spec Assumption explicitly reuses "the existing payment-request authorization mechanism used today (no new auth system is introduced by this feature)." The QR payload is the capability; adding OAuth is out of scope.
- **Alternatives considered**:
  - Require a signed fetch token issued at QR generation time: stronger but larger scope; can be added later without breaking the URL shape.
  - Session-based auth: no payer session exists in this system. Rejected.

## R9. Retry & failure isolation for push

- **Decision**: Dispatch is fire-and-forget from the webhook path (event-driven). Inside the push module, an outbound Expo call retries transient failures (HTTP 5xx, network) with exponential backoff, capped at 3 attempts over ~30 s. Permanent failures (4xx other than auth) are logged and dropped. A single failing token never blocks dispatch to other tokens — the token list is dispatched in one Expo batch (Expo supports up to 100 messages per request).
- **Rationale**: Matches FR-015. Bounded retry avoids pathological queues; batching matches Expo's recommended shape and keeps one webhook → one Expo request in typical cases.

## R10. SSE removal staging

- **Decision**: Land push + status endpoint behind no feature flag (spec doesn't ask for one), but **do not delete** `QrPaymentSseController` / `PaymentUpdateBus` in the same PR. Removal is the final task in `tasks.md`, done as a separate commit (`refactor: remove obsolete QR SSE stream`) after the mobile client cuts over.
- **Rationale**: Matches spec User Story 4 and FR-013. Keeping the removal isolated makes rollback trivial if the mobile rollout slips.

## R11. Observability

- **Decision**: Structured logs (`kotlin-logging`) on: webhook received, status transition applied, `PaymentStatusChanged` emitted, Expo dispatch started/completed, receipt outcome, token invalidated. No new metrics stack introduced — rely on the existing `mobileobservability` module if emission is wired there; otherwise logs are the observability contract for this slice.
- **Rationale**: FR-011. Avoid scope creep into metrics infra.

## R12. Contract-test profile compatibility

- **Decision**: Provide a mocked `ExpoPushClient` bean under the `contract-test` profile analogous to how TrueLayer beans are mocked today. Contract tests for the new controllers do not hit Expo.
- **Rationale**: Matches `CLAUDE.md`: "The `contract-test` profile excludes DataSource, JPA, and Modulith event persistence auto-configuration; TrueLayer beans are mocked."

---

All items in the Technical Context are resolved. Proceeding to Phase 1 design.
