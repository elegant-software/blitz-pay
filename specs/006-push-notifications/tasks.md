---
description: "Task list for feature 006-push-notifications"
---

# Tasks: Push Notifications for Payment Status

**Input**: Design documents from `/specs/006-push-notifications/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included — the existing project contract-test + unit-test conventions (per `CLAUDE.md`: JUnit 5 + Mockito Kotlin, `WebTestClient` contract tests, Modulith `ApplicationModules.verify()`) are preserved throughout.

**Organization**: Tasks are grouped by user story from `spec.md` (US1, US2, US3, US4) and ordered so that each story is independently shippable once Phase 1 + Phase 2 are complete.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)

## Path Conventions

Single-module Spring Boot project (per `plan.md` Structure Decision):

- Main sources: `src/main/kotlin/com/elegant/software/blitzpay/payments/push/...`
- Unit tests: `src/test/kotlin/com/elegant/software/blitzpay/payments/push/...`
- Contract tests: `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/push/...`
- Contract resources: `src/contractTest/resources/contracts/push/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold the new `payments.push` Spring Modulith module and wire Expo configuration.

- [X] T001 Create package tree `src/main/kotlin/com/elegant/software/blitzpay/payments/push/{api,internal,persistence,config}` (empty `.gitkeep` or initial `PackageInfo.kt`).
- [X] T002 Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/PackageInfo.kt` annotated with `@org.springframework.modulith.PackageInfo` + `@ApplicationModule(displayName = "payments.push")` and declare `@NamedInterface("api")` on `api/`.
- [X] T003 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/config/ExpoPushProperties.kt` — `@ConfigurationProperties(prefix = "blitzpay.expo")` binding `accessToken`, `baseUrl` (default `https://exp.host/--/api/v2/push/send`), `requestTimeoutMs`, `maxBatchSize` (default 100).
- [X] T004 [P] Add `EXPO_ACCESS_TOKEN` binding in `src/main/resources/application.yml` under `blitzpay.expo.access-token: ${EXPO_ACCESS_TOKEN:}` and document the variable in `CLAUDE.md` "Required Environment Variables" section.
- [ ] T005 [P] Create test fixture dir `src/test/resources/testdata/push/` and add sample Expo ticket/receipt JSON fixtures `expo-ticket-ok.json`, `expo-ticket-device-not-registered.json`, `expo-receipt-ok.json`, `expo-receipt-device-not-registered.json`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Persistence + domain event + Modulith verification that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Create enum `PaymentStatusCode` (`PENDING, EXECUTED, SETTLED, FAILED, EXPIRED`) with `isTerminal()` helper in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/PaymentStatusCode.kt` (public — part of `@NamedInterface("api")`).
- [X] T007 [P] Create `PaymentStatusChanged` domain event data class in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/PaymentStatusChanged.kt` with fields `paymentRequestId`, `newStatus`, `previousStatus`, `occurredAt`, `sourceEventId` (per `data-model.md`).
- [X] T008 [P] JPA entity + repository: `src/main/kotlin/com/elegant/software/blitzpay/payments/push/persistence/PaymentStatusEntity.kt` and `PaymentStatusRepository.kt` (PK `paymentRequestId`, fields per `data-model.md`, `@Version` on `version`).
- [X] T009 [P] JPA entity + repository: `src/main/kotlin/com/elegant/software/blitzpay/payments/push/persistence/DeviceRegistrationEntity.kt` and `DeviceRegistrationRepository.kt` with `UNIQUE(expoPushToken)`, `findByPaymentRequestIdAndInvalidFalse`, `findByExpoPushToken`.
- [X] T010 [P] JPA entity + repository: `src/main/kotlin/com/elegant/software/blitzpay/payments/push/persistence/ProcessedWebhookEventEntity.kt` and `ProcessedWebhookEventRepository.kt` (PK `eventId`, insert-if-absent helper).
- [X] T011 [P] JPA entity + repository (optional observability): `src/main/kotlin/com/elegant/software/blitzpay/payments/push/persistence/PushDeliveryAttemptEntity.kt` and `PushDeliveryAttemptRepository.kt` per `data-model.md`.
- [ ] T012 Update Modulith module-verification test in `src/test/kotlin/com/elegant/software/blitzpay/ApplicationModulesTest.kt` so `ApplicationModules.of(QuickpayApplication::class.java).verify()` recognizes `payments.push` as a module and allows `payments.truelayer` → `payments.push.api` event dependency only.
- [ ] T013 [P] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/persistence/PaymentStatusRepositoryTest.kt` — `@DataJpaTest` verifying upsert + optimistic-lock monotonic update.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Payer receives push notification on payment completion (Priority: P1) 🎯 MVP

**Goal**: On a verified TrueLayer webhook for `payment_executed` / `payment_settled`, deliver an Expo Push to every valid registered device for that payment request.

**Independent Test**: With a fake Expo endpoint (WireMock) and a seeded `DeviceRegistration` row, POST a signed `payment_settled` webhook payload and assert (a) `PaymentStatus` row is updated, (b) Expo receives one batched push request, (c) `PushDeliveryAttempt` row is written with outcome `ACCEPTED`.

### Tests for User Story 1

> Write these FIRST and ensure they FAIL before implementation.

- [ ] T014 [P] [US1] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentStatusServiceTest.kt` — monotonic transition, ignore older events, idempotent re-apply.
- [ ] T015 [P] [US1] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/internal/ExpoPushClientTest.kt` — builds correct payload, batches up to 100, maps `DeviceNotRegistered` / `InvalidCredentials` tickets to `REJECTED`.
- [ ] T016 [P] [US1] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentStatusChangedListenerTest.kt` — loads only non-invalid tokens, dispatches once, records `PushDeliveryAttempt` rows.
- [ ] T017 [P] [US1] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/internal/ExpoReceiptPollerTest.kt` — marks token `invalid=true` when receipt returns `DeviceNotRegistered`.
- [ ] T018 [P] [US1] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/truelayer/inbound/WebhookControllerTest.kt` additions — dedupes on `ProcessedWebhookEvent`, publishes `PaymentStatusChanged` only on first delivery.

### Implementation for User Story 1

- [X] T019 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentStatusService.kt` — upserts `PaymentStatusEntity` with monotonic-transition guard; returns `previousStatus` for event emission.
- [X] T020 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/ExpoPushClient.kt` — `WebClient` POST to `ExpoPushProperties.baseUrl`, `Authorization: Bearer ...`, batched up to 100 messages, parses tickets, bounded retry (3 attempts, exponential backoff) on 5xx/IO per `research.md` R9.
- [X] T021 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/PushDispatcher.kt` — orchestrates token lookup → Expo call → `PushDeliveryAttempt` logging; swallows per-token failures so they don't block the batch.
- [X] T022 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentStatusChangedListener.kt` — `@ApplicationModuleListener` on `PaymentStatusChanged`, delegates to `PushDispatcher`.
- [X] T023 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/ExpoReceiptPoller.kt` — scheduled task (`@Scheduled` or Modulith-triggered) that queries Expo receipts ~15 min after send and flips `DeviceRegistration.invalid` for error codes (`DeviceNotRegistered`, `InvalidCredentials`).
- [X] T024 [US1] Edit `src/main/kotlin/com/elegant/software/blitzpay/payments/truelayer/inbound/WebhookController.kt` to: (a) insert into `ProcessedWebhookEventRepository` and short-circuit on conflict, (b) call `PaymentStatusService.apply(...)`, (c) publish `PaymentStatusChanged` via `ApplicationEventPublisher` (only on a real transition).
- [X] T025 [US1] Add `@Configuration` bean wiring in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/config/PushConfiguration.kt` — `WebClient` with timeout, `ExpoPushClient`, scheduler for `ExpoReceiptPoller`; register `ExpoPushProperties` via `@EnableConfigurationProperties`.
- [X] T026 [US1] Add mock `ExpoPushClient` primary bean under the `contract-test` profile at `src/contractTest/kotlin/com/elegant/software/blitzpay/support/ContractTestConfig.kt` (extend existing class if present) so contract tests don't hit Expo.
- [X] T027 [US1] Structured logging (`kotlin-logging`) in `PushDispatcher`, `ExpoPushClient`, `PaymentStatusService`, `WebhookController` per spec FR-011 / `research.md` R11.

**Checkpoint**: US1 complete — the MVP slice. Push arrives on the device on webhook events.

---

## Phase 4: User Story 2 - Reliable status fallback via direct query (Priority: P1)

**Goal**: Expose `GET /v1/payments/{paymentRequestId}` returning authoritative current status; 404 for unknown or unauthorized.

**Independent Test**: With a seeded `PaymentStatusEntity`, `GET /v1/payments/{id}` returns 200 + correct status; GET with a random UUID returns 404 with a generic `Problem+json` body.

### Tests for User Story 2

- [X] T028 [P] [US2] Contract test `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/push/PaymentStatusControllerContractTest.kt` (WebTestClient) covering 200 (settled / pending), 404 (unknown), 400 (malformed UUID) per `contracts/payments-get.yaml`.
- [ ] T029 [P] [US2] Groovy contract `src/contractTest/resources/contracts/push/get-payment-status.groovy` mirroring the OpenAPI examples.

### Implementation for User Story 2

- [X] T030 [US2] Create DTO `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/PaymentStatusResponse.kt` (fields per `contracts/payments-get.yaml`: `paymentRequestId`, `status`, `terminal`, `lastEventAt`).
- [X] T031 [US2] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/PaymentStatusController.kt` — `@RestController`, `@GetMapping("/v1/payments/{paymentRequestId}")`, returns `ResponseEntity<PaymentStatusResponse>`; 404 for missing, 400 for malformed UUID via `@ExceptionHandler`.
- [X] T032 [US2] Query method in `PaymentStatusService.getByRequestId(...)` returning `Optional<PaymentStatusResponse>`; no information leak in error paths.
- [X] T033 [US2] Register the new endpoint with the existing `PathOnlyApiVersionResolver` (verify no collision with Swagger); add OpenAPI metadata via the existing `OpenApiConfig.kt` conventions.

**Checkpoint**: US2 complete — fallback query is live. Combined with US1, the push + pull reliability goal is met.

---

## Phase 5: User Story 3 - Device registration and lifecycle management (Priority: P2)

**Goal**: Payer app can register, refresh, unregister Expo push tokens; invalid tokens are auto-retired.

**Independent Test**: `POST /v1/devices` with a new token → 201 and a row appears; repeat → 200 and `lastSeenAt` advances; `DELETE /v1/devices/{token}` → 204 and the row is gone; simulating an Expo receipt error for a token flips `invalid=true`.

### Tests for User Story 3

- [ ] T034 [P] [US3] Unit test `src/test/kotlin/com/elegant/software/blitzpay/payments/push/internal/DeviceRegistrationServiceTest.kt` — create vs. refresh idempotency, invalid payload rejection.
- [X] T035 [P] [US3] Contract test `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/push/DeviceRegistrationControllerContractTest.kt` covering `POST /v1/devices` 201/200/400/404 and `DELETE /v1/devices/{token}` 204 per `contracts/device-register.yaml`.
- [ ] T036 [P] [US3] Groovy contract `src/contractTest/resources/contracts/push/register-device.groovy`.

### Implementation for User Story 3

- [X] T037 [P] [US3] DTOs in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/DeviceRegistrationDtos.kt` — `DeviceRegistrationRequest`, `DeviceRegistrationResponse`, `DevicePlatform` enum.
- [X] T038 [US3] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/DeviceRegistrationService.kt` — idempotent upsert on `expoPushToken` (`UNIQUE` handled at repo layer), rebinding `paymentRequestId` on refresh; unregister-by-token.
- [X] T039 [US3] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/DeviceRegistrationController.kt` — `POST /v1/devices` (return 201 vs 200 based on create vs refresh), `DELETE /v1/devices/{expoPushToken}` (always 204), Bean Validation for the token regex `^ExponentPushToken\[[^\]]+\]$`.
- [X] T040 [US3] Wire `DeviceRegistrationService` into `ExpoReceiptPoller` so it can call `markInvalid(token)` — ensures FR-007 closes end-to-end.

**Checkpoint**: US3 complete — device lifecycle is under the backend's control.

---

## Phase 6: User Story 4 - Remove obsolete SSE streaming path (Priority: P3)

**Goal**: Once mobile clients are migrated, delete `QrPaymentSseController` and `PaymentUpdateBus`; return a clear deprecation response to any residual consumer in the interim.

**Independent Test**: After deletion, the full QR payment flow (create request → webhook → push → status query) passes end-to-end; a GET to the old SSE URL returns 410 Gone (or 404).

> Do **not** execute this phase until mobile client telemetry shows no active SSE consumers for a representative window. Land it as a separate commit.

- [ ] T041 [US4] Intermediate: replace `QrPaymentSseController` handler body with a `@ResponseStatus(HttpStatus.GONE)` handler that returns a `Problem+json` linking to the new endpoints (temporary — until removal).
- [ ] T042 [US4] Delete `src/main/kotlin/com/elegant/software/blitzpay/payments/qrpay/QrPaymentSseController.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/support/PaymentUpdateBus.kt`.
- [ ] T043 [US4] Remove `PaymentUpdateBus` constructor injection and usages from `src/main/kotlin/com/elegant/software/blitzpay/payments/qrpay/PaymentRequestController.kt` and `PaymentInitRequestListener.kt`; adjust any tests that referenced them.
- [ ] T044 [US4] Update the `payments.qrpay` `PackageInfo` / module description in `CLAUDE.md` `### Modules` section — drop the "`QrPaymentSseController` streams status updates via SSE" line and the `PaymentUpdateBus` reference.
- [ ] T045 [US4] Delete or rewrite any SSE-related contract tests under `src/contractTest/` that referenced the removed endpoint.

**Checkpoint**: All user stories complete; obsolete transport removed.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T046 [P] Run `./gradlew check` (unit + contract). Fix any regressions.
- [ ] T047 [P] Run the `specs/006-push-notifications/quickstart.md` end-to-end locally (requires `EXPO_ACCESS_TOKEN` against a sandbox Expo project).
- [ ] T048 [P] Ensure retention pruning is tracked (create a follow-up issue, not implemented here) for `DeviceRegistration` + `PushDeliveryAttempt` per `data-model.md`.
- [ ] T049 [P] Update `reference/api-versioning-guide.md` (if it lists endpoints) with `GET /v1/payments/{id}`, `POST /v1/devices`, `DELETE /v1/devices/{token}`.
- [ ] T050 Squash fixup commits and verify commit log reads as separate `feat:` / `refactor:` commits per `CLAUDE.md` Commit Convention (e.g., `feat: add payments.push module`, `feat: add payment status fallback endpoint`, `feat: device registration API`, `refactor: remove obsolete QR SSE stream`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → no dependencies.
- **Foundational (Phase 2)** → depends on Phase 1. BLOCKS all user stories.
- **US1 (Phase 3, P1)** → depends on Phase 2.
- **US2 (Phase 4, P1)** → depends on Phase 2. Independent of US1 but naturally ships alongside.
- **US3 (Phase 5, P2)** → depends on Phase 2. Required at runtime for US1 to have tokens to fan out to, but can be implemented in parallel.
- **US4 (Phase 6, P3)** → depends on US1 + US2 being live AND mobile-client migration. Gated manually.
- **Polish (Phase 7)** → after all intended stories land.

### Within Each User Story

- Tests (where marked) are written first and must fail before implementation.
- Entity/repo → service → controller/listener → configuration/wiring → logging.

### Parallel Opportunities

- Phase 1: T003, T004, T005 in parallel (different files).
- Phase 2: T006–T011, T013 in parallel (separate files); T012 sequenced last because it imports the new module types.
- Phase 3 tests T014–T018 all in parallel.
- Phase 4 tests T028, T029 in parallel.
- Phase 5 tests T034–T036 in parallel; DTO task T037 parallel with tests.
- Phase 7: all polish tasks in parallel.

---

## Parallel Example: User Story 1 tests

```bash
# Launch all US1 tests together (each touches a different file):
Task: "Unit test PaymentStatusServiceTest"                  # T014
Task: "Unit test ExpoPushClientTest"                        # T015
Task: "Unit test PaymentStatusChangedListenerTest"          # T016
Task: "Unit test ExpoReceiptPollerTest"                     # T017
Task: "Unit test WebhookControllerTest additions"           # T018
```

---

## Implementation Strategy

### MVP First (US1 + US2 together, shipped as one release)

1. Phase 1 Setup.
2. Phase 2 Foundational.
3. Phase 3 US1 (push on webhook) **and** Phase 4 US2 (status endpoint) — they are both P1 and together satisfy the spec's SC-001 + SC-002 reliability goal.
4. Validate via `quickstart.md`. Ship. Mobile client cuts over.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 + US2 together (P1, P1) → ship; mobile switches to push + fallback.
3. US3 (P2) → ship; lifecycle now backend-managed (in practice, most of US3 will land with US1 since without registration there is nothing to push to — see "Practical Reorder" note below).
4. US4 (P3) → ship after mobile rollout window; SSE is gone.

### Practical Reorder Note

Strictly by priority, US3 is P2. But at runtime, US1 cannot emit a single notification unless US3's `POST /v1/devices` exists. In execution, implement `DeviceRegistrationService` + controller (T037–T039) as part of the US1 PR so the MVP is truly demoable. The P2 label remains accurate for independent *test* scope (US3 is testable without the webhook path).

---

## Notes

- Every task above follows `- [ ] Tnnn [P?] [US?] description + file path`.
- No task touches files outside the repository.
- `ddl-auto: update` handles schema creation for all new tables on startup — no migration task needed (matches project convention).
- `ApplicationModules.verify()` (T012) is the guardrail that keeps cross-module coupling honest; do not skip it.
- Commit after each phase checkpoint; squash fixups before merging per `CLAUDE.md`.
