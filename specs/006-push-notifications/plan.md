# Implementation Plan: Push Notifications for Payment Status

**Branch**: `006-push-notifications` | **Date**: 2026-04-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-push-notifications/spec.md`

## Summary

Replace the current SSE-based QR payment status stream with a push-notification delivery path driven by the existing TrueLayer webhook, plus a pull-based status fallback endpoint. When a TrueLayer webhook for `payment_executed` / `payment_settled` is verified and persisted, an `@ApplicationModuleListener` in a new `payments.push` module fans out an Expo Push request (APNs + FCM under the hood) to every valid device token registered against the payment request. A new `GET /v1/payments/{paymentRequestId}` endpoint returns the authoritative current status so the mobile client stays correct even when push delivery is delayed, dropped, or OS-suppressed. The existing `QrPaymentSseController` and transient in-memory `PaymentUpdateBus` become obsolete and will be removed in a final, gated slice once the mobile client has migrated.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25 (unchanged)
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA on PostgreSQL 16, TrueLayer Java SDK (unchanged). New: Reactor `WebClient` against the Expo Push HTTPS API (`https://exp.host/--/api/v2/push/send`) — no additional SDK; a thin in-repo client keeps the dependency surface minimal.
**Storage**: PostgreSQL via JPA (`ddl-auto: update`, no migration framework — matches current project convention). Two new tables: `payment_status` (authoritative current state per payment request) and `device_registration` (Expo push tokens per payment request / payer). Optional `push_delivery_attempt` for observability; kept in-memory-only if retention cost is a concern.
**Testing**: JUnit 5 + Mockito Kotlin for unit; `WebTestClient`-based contract tests in `src/contractTest/kotlin` under the `contract-test` profile (TrueLayer + Expo beans mocked); Spring Modulith `ApplicationModules.of(...).verify()` maintained.
**Target Platform**: Linux server (Spring Boot reactive), deployed to Kubernetes (per repo `infra/`); consumers are iOS 15+ and Android via an Expo-managed React Native app.
**Project Type**: Web service (existing Spring Modulith monolith). Mobile client is a separate repo — out of scope for this plan.
**Performance Goals**: Push fan-out dispatched within 500 ms of webhook acknowledgement (p95); status endpoint p95 under 200 ms. Webhook throughput continues to match TrueLayer's signed-delivery rate (single-digit RPS in normal operation, burst to tens).
**Constraints**: Webhook handling must remain non-blocking on the reactive pipeline; push delivery must not back-pressure webhook ACKs (fire-and-forget with bounded retry). Status endpoint must not leak existence of unauthorized payment requests. No new secrets beyond Expo access token.
**Scale/Scope**: Order of 10k payments/day; 1–2 devices per active payment; token corpus grows with payer base — bounded retention prunes after payment terminal state + 30 days.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The project constitution at `.specify/memory/constitution.md` is an unpopulated template (all placeholders). No binding principle gates apply. Planning proceeds under the effective conventions documented in `CLAUDE.md` and `CONTRIBUTING.md`:

- **Module boundaries (Spring Modulith)**: new `payments.push` module — no direct bean coupling across modules; communication via `ApplicationEventPublisher` / `@ApplicationModuleListener`. ✅
- **API versioning**: new endpoint under `/v1/...` using the existing `PathOnlyApiVersionResolver`. ✅
- **Persistence**: JPA with `ddl-auto: update`, no migration framework. ✅
- **Testing**: unit + `WebTestClient` contract tests; TrueLayer and Expo mocked under `contract-test`. ✅
- **Commit style**: semantic commits (`feat:`, `refactor:`, `chore:`). ✅

**Gate result**: PASS (no violations → Complexity Tracking section empty).

## Project Structure

### Documentation (this feature)

```text
specs/006-push-notifications/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── payments-get.yaml
│   └── device-register.yaml
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # Created later by /speckit.tasks
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── payments/
│   ├── truelayer/                     # existing — unchanged public surface
│   │   └── inbound/
│   │       └── WebhookController.kt   # publishes PaymentStatusChanged event (new)
│   ├── qrpay/
│   │   ├── PaymentRequestController.kt  # unchanged (creates requests)
│   │   ├── PaymentInitRequestListener.kt# unchanged
│   │   └── QrPaymentSseController.kt    # DEPRECATED → removed in final slice
│   ├── push/                          # NEW MODULE
│   │   ├── PackageInfo.kt             # @PackageInfo @ApplicationModule, @NamedInterface
│   │   ├── api/
│   │   │   ├── DeviceRegistrationController.kt
│   │   │   └── PaymentStatusController.kt   # GET /v1/payments/{id}
│   │   ├── internal/
│   │   │   ├── PaymentStatusChangedListener.kt  # @ApplicationModuleListener
│   │   │   ├── ExpoPushClient.kt
│   │   │   ├── DeviceRegistrationService.kt
│   │   │   └── PaymentStatusService.kt
│   │   ├── persistence/
│   │   │   ├── DeviceRegistration.kt + Repository
│   │   │   ├── PaymentStatus.kt + Repository
│   │   │   └── (optional) PushDeliveryAttempt.kt + Repository
│   │   └── config/
│   │       └── ExpoPushProperties.kt
│   └── support/
│       └── PaymentUpdateBus.kt        # DEPRECATED → removed with SSE controller
└── config/                            # unchanged

src/test/kotlin/com/elegant/software/blitzpay/payments/push/
├── PaymentStatusChangedListenerTest.kt
├── ExpoPushClientTest.kt
├── DeviceRegistrationServiceTest.kt
└── PaymentStatusServiceTest.kt

src/contractTest/kotlin/com/elegant/software/blitzpay/payments/push/
├── PaymentStatusControllerContractTest.kt
└── DeviceRegistrationControllerContractTest.kt

src/contractTest/resources/contracts/push/
├── get-payment-status.groovy
└── register-device.groovy
```

**Structure Decision**: Extend the existing Spring Modulith monolith with a new `payments.push` application module that owns device registrations, Expo dispatch, and the authoritative payment-status read model. The TrueLayer module keeps ownership of webhook ingestion and gains a single new outbound domain event (`PaymentStatusChanged`). The QR module's SSE surface is deprecated but not removed in the same slice — removal is scheduled as the final task once the mobile client no longer consumes it. No new repositories, no new services (Kafka/Redis/etc.), consistent with current project simplicity.

## Complexity Tracking

> No constitution violations to justify — section intentionally empty.
