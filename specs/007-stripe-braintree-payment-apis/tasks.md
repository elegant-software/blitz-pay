# Tasks: Stripe & Braintree Payment APIs

**Input**: Design documents from `specs/007-stripe-braintree-payment-apis/`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Kotlin source root: `src/main/kotlin/com/elegant/software/blitzpay`
- Test root: `src/test/kotlin/com/elegant/software/blitzpay`
- Contract test root: `src/contractTest/kotlin/com/elegant/software/blitzpay`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add Stripe and Braintree SDK dependencies so all subsequent tasks can compile.

- [x] T001 Add `stripe` version entry (`stripe-java = "28.x"`, resolve latest stable) and `stripe-java` library alias to `gradle/libs.versions.toml` under `[versions]` and `[libraries]` sections
- [x] T002 Add `braintree` version entry (`braintree-java = "3.43.0"`) and `braintree-java` library alias to `gradle/libs.versions.toml` under `[versions]` and `[libraries]` sections
- [x] T003 [P] Add `implementation(libs.stripe.java)` and `implementation(libs.braintree.java)` to the `dependencies` block in `build.gradle.kts`
- [x] T004 [P] Add `STRIPE_SECRET_KEY`, `EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY`, `BRAINTREE_MERCHANT_ID`, `BRAINTREE_PUBLIC_KEY`, `BRAINTREE_PRIVATE_KEY`, `BRAINTREE_ENVIRONMENT` to the "Required Environment Variables" section in `CLAUDE.md`

**Checkpoint**: `./gradlew dependencies` resolves without errors; `stripe-java` and `braintree-java` appear in the dependency tree.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared application configuration and contract-test infrastructure that both modules depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T005 Add `stripe` and `braintree` property groups to `src/main/resources/application.yml`: `stripe.secret-key`, `stripe.publishable-key` bound to `STRIPE_SECRET_KEY` / `EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY`; `braintree.merchant-id`, `braintree.public-key`, `braintree.private-key`, `braintree.environment` bound to their respective env vars
- [x] T006 Extend `src/contractTest/kotlin/com/elegant/software/blitzpay/support/ContractTestConfig.kt` to provide `@MockBean` (or `@Bean` stubs) for the Stripe `com.stripe.Stripe` configuration and for the Braintree `BraintreeGateway` optional bean, so contract tests load without real credentials

**Checkpoint**: `./gradlew contractTest` still compiles and runs (existing contract tests remain green).

---

## Phase 3: User Story 1 — Card Payment via Stripe (Priority: P1) 🎯 MVP

**Goal**: Mobile clients can request a Stripe PaymentIntent; the server returns the `client_secret` and `publishableKey` needed by the mobile Stripe SDK.

**Independent Test**: `POST /v1/payments/stripe/create-intent` with `{"amount": 12.50}` returns a JSON body containing `paymentIntent` (non-blank string) and `publishableKey` (non-blank string). Invalid amount returns HTTP 400.

### Implementation for User Story 1

- [x] T007 [P] [US1] Create `src/main/kotlin/.../payments/stripe/package-info.kt` declaring `@ApplicationModule` with `allowedDependencies = []` (no cross-module dependencies needed)
- [x] T008 [P] [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeProperties.kt` as `@ConfigurationProperties(prefix = "stripe")` with `secretKey: String` and `publishableKey: String` fields
- [x] T009 [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeConfig.kt` with a `@Bean` that sets `Stripe.apiKey = properties.secretKey` on application startup (Stripe SDK uses a static API key)
- [x] T010 [P] [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeOpenApiConfig.kt` registering a `GroupedOpenApi` bean named `"Stripe"` for paths `/v1/payments/stripe/**`
- [x] T011 [US1] Create `src/main/kotlin/.../payments/stripe/internal/StripePaymentService.kt` with a `createIntent(amount: Double, currency: String): StripeIntentResult` method that calls `PaymentIntents.create(...)` wrapped in `Mono.fromCallable { }.subscribeOn(Schedulers.boundedElastic())`, validates `amount > 0`, and returns `client_secret` + publishable key
- [x] T012 [US1] Create `src/main/kotlin/.../payments/stripe/internal/StripePaymentController.kt` mapping `POST /v1/payments/stripe/create-intent`, accepts `{"amount": Double, "currency": String?}`, delegates to `StripePaymentService`, returns `{"paymentIntent": ..., "publishableKey": ...}` on success or `{"error": ...}` with HTTP 400/500
- [x] T013 [P] [US1] Create `src/test/kotlin/.../payments/stripe/StripePaymentServiceTest.kt` with unit tests covering: happy path returns client secret, zero amount throws validation error, negative amount throws validation error, Stripe API exception maps to 500 response
- [x] T014 [P] [US1] Create `src/contractTest/kotlin/.../payments/stripe/StripePaymentControllerContractTest.kt` with `WebTestClient` contract tests: valid amount → 200 with `paymentIntent` and `publishableKey` fields present; missing amount → 400; invalid amount (zero) → 400
- [x] T015 [US1] Update `src/test/kotlin/.../quickpay/payments/ModularityTest.kt.kt` to add `payments.stripe` to the `ApplicationModules.of(...)` verification so module boundary violations are caught

**Checkpoint**: `./gradlew check` passes. Smoke test from quickstart.md Stripe section succeeds against a real sandbox key.

---

## Phase 4: User Story 2 — PayPal / Digital Wallet via Braintree (Priority: P2)

**Goal**: Mobile clients can fetch a Braintree client token and submit a payment nonce for settlement. When Braintree is unconfigured, both endpoints return HTTP 503.

**Independent Test**: (1) `POST /v1/payments/braintree/client-token` returns `{"clientToken": "<non-blank>"}`. (2) `POST /v1/payments/braintree/checkout` with a sandbox nonce and `amount: 12.50` returns `{"status": "succeeded", "transactionId": "<non-blank>", ...}`. (3) Both endpoints return `{"error": "Braintree not configured on server"}` with HTTP 503 when env vars are absent.

### Implementation for User Story 2

- [x] T016 [P] [US2] Create `src/main/kotlin/.../payments/braintree/package-info.kt` declaring `@ApplicationModule` with `allowedDependencies = []`
- [x] T017 [P] [US2] Create `src/main/kotlin/.../payments/braintree/config/BraintreeProperties.kt` as `@ConfigurationProperties(prefix = "braintree")` with `merchantId: String`, `publicKey: String`, `privateKey: String`, `environment: String` (default `"sandbox"`)
- [x] T018 [US2] Create `src/main/kotlin/.../payments/braintree/config/BraintreeConfig.kt` with a `@Bean @ConditionalOnProperty(name = ["braintree.merchant-id"])` that constructs `BraintreeGateway` from properties; absent env vars = bean not created
- [x] T019 [P] [US2] Create `src/main/kotlin/.../payments/braintree/config/BraintreeOpenApiConfig.kt` registering a `GroupedOpenApi` bean named `"Braintree"` for paths `/v1/payments/braintree/**`
- [x] T020 [US2] Create `src/main/kotlin/.../payments/braintree/internal/BraintreePaymentService.kt` with: `generateClientToken(): String` (calls `gateway.clientToken.generate({})` on `Schedulers.boundedElastic()`), and `checkout(nonce: String, amount: Double, currency: String): BraintreeCheckoutResult` (calls `gateway.transaction.sale(...)` with `submitForSettlement = true`); validates `nonce` non-blank and `amount > 0`
- [x] T021 [US2] Create `src/main/kotlin/.../payments/braintree/internal/BraintreePaymentController.kt` with: `POST /v1/payments/braintree/client-token` (checks gateway present, returns `{"clientToken": ...}` or 503); `POST /v1/payments/braintree/checkout` (validates request, delegates to service, returns success/failure body or 503)
- [x] T022 [P] [US2] Create `src/test/kotlin/.../payments/braintree/BraintreePaymentServiceTest.kt` covering: client token success, client token Braintree API failure → maps to 500, checkout success, checkout decline returns failed status, missing nonce → 400, zero amount → 400
- [x] T023 [P] [US2] Create `src/contractTest/kotlin/.../payments/braintree/BraintreePaymentControllerContractTest.kt` with `WebTestClient` contract tests: client-token 200, client-token 503 (gateway absent), checkout success 200, checkout missing nonce 400, checkout 503 (gateway absent)
- [x] T024 [US2] Update `src/test/kotlin/.../quickpay/payments/ModularityTest.kt.kt` to add `payments.braintree` to the `ApplicationModules.of(...)` verification

**Checkpoint**: `./gradlew check` passes. Braintree sandbox smoke tests from quickstart.md succeed.

---

## Phase 5: User Story 3 — Invoice-linked Payments (Priority: P3)

**Goal**: Braintree checkout accepts an optional `invoiceId` field and echoes it in the success response, enabling client-side payment-to-invoice reconciliation.

**Independent Test**: `POST /v1/payments/braintree/checkout` with `{"nonce": "...", "amount": 12.50, "invoiceId": "INV-001"}` returns a success body that includes `"invoiceId": "INV-001"`. A checkout without `invoiceId` still succeeds normally.

### Implementation for User Story 3

- [x] T025 [US3] Extend the checkout request data class in `src/main/kotlin/.../payments/braintree/internal/BraintreePaymentController.kt` to add `val invoiceId: String? = null`; extend the success response body to include `invoiceId` when non-null
- [x] T026 [US3] Extend `BraintreePaymentService.kt` `checkout` method signature to accept `invoiceId: String?` and include it in the returned `BraintreeCheckoutResult`; log `invoice=<invoiceId ?? "n/a">` alongside transaction ID for traceability
- [x] T027 [US3] Add invoice-linked checkout contract test scenario to `src/contractTest/kotlin/.../payments/braintree/BraintreePaymentControllerContractTest.kt`: request with `invoiceId` → response contains matching `invoiceId`; request without `invoiceId` → response has no `invoiceId` field (or null)

**Checkpoint**: `./gradlew check` passes. All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Wiring, observability, and final validation across both modules.

- [x] T028 [P] Add `Stripe` and `Braintree` entries to the `springdoc.swagger-ui.urls` list in `src/main/resources/application.yml` so both API groups appear in the Swagger UI dropdown
- [x] T029 [P] Update the `## Modules` section in `CLAUDE.md` to document `payments.stripe` and `payments.braintree` with their public surfaces and endpoint paths
- [x] T030 Run `./gradlew check` to confirm all unit tests and contract tests pass for both new modules and no existing tests regress
- [ ] T031 Validate all three endpoints against a live sandbox using the smoke test commands in `specs/007-stripe-braintree-payment-apis/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Requires Phase 1 — BLOCKS all user stories
- **Phase 3 (US1 Stripe)**: Requires Phase 2 — independent of US2/US3
- **Phase 4 (US2 Braintree)**: Requires Phase 2 — independent of US1/US3
- **Phase 5 (US3 Invoice)**: Requires Phase 4 (extends Braintree checkout)
- **Phase 6 (Polish)**: Requires all desired stories complete

### User Story Dependencies

- **US1 (P1)**: Depends only on Foundational. No dependency on US2 or US3.
- **US2 (P2)**: Depends only on Foundational. No dependency on US1 or US3.
- **US3 (P3)**: Depends on US2 (extends Braintree checkout with invoiceId).

### Within Each User Story

- Config (`@ConfigurationProperties`, `@Bean`) → Service → Controller
- Unit tests and contract tests can be written in parallel with service/controller (different files)
- Modularity verification (T015, T024) runs last within each story

### Parallel Opportunities

- T001 and T002 can run in parallel (different sections of the same file — sequence if only one agent)
- T003 and T004 can run in parallel
- T007, T008, T010 can run in parallel (different files within US1 setup)
- T013 and T014 can run in parallel (test vs contract test, different files)
- T016, T017, T019 can run in parallel (different files within US2 setup)
- T022 and T023 can run in parallel
- US1 (Phase 3) and US2 (Phase 4) can run in parallel on separate branches once Phase 2 is done
- T028 and T029 can run in parallel

---

## Parallel Example: User Story 1

```
# After Phase 2 complete, launch in parallel:
Task T007: payments/stripe/package-info.kt
Task T008: payments/stripe/config/StripeProperties.kt
Task T010: payments/stripe/config/StripeOpenApiConfig.kt

# Then (depends on T008):
Task T009: StripeConfig.kt

# Then (depends on T009):
Task T011: StripePaymentService.kt

# In parallel with T011:
Task T013: StripePaymentServiceTest.kt  ← write alongside service

# Then (depends on T011):
Task T012: StripePaymentController.kt

# In parallel with T012:
Task T014: StripePaymentControllerContractTest.kt  ← write alongside controller
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T006)
3. Complete Phase 3: US1 Stripe (T007–T015)
4. **STOP and VALIDATE**: `./gradlew check` + Stripe smoke test
5. Ship Stripe card payment capability

### Incremental Delivery

1. Setup + Foundational → both SDKs on classpath, contract tests still green
2. US1 Stripe → Stripe card payment live → validate → deploy
3. US2 Braintree → PayPal/digital wallet live → validate → deploy
4. US3 Invoice linking → invoice reconciliation live → validate → deploy
5. Polish → Swagger, docs, final regression

### Parallel Team Strategy

With two developers after Phase 2 completes:
- Developer A: US1 (T007–T015) — Stripe module
- Developer B: US2 (T016–T024) — Braintree module
- Both converge at Phase 6 Polish

---

## Notes

- `[P]` tasks touch different files — safe for parallel execution by separate agents
- US1 and US2 are fully independent after Phase 2 — different modules, different SDKs, different test classes
- US3 extends US2; do not start US3 until T020/T021 are committed
- Stripe SDK uses a static `Stripe.apiKey` — only one `@Bean` initialization needed per application context
- Braintree `BraintreeGateway` bean is `@ConditionalOnProperty`; contract tests must supply a mock in `ContractTestConfig`
- Use `SLF4J` (`LoggerFactory.getLogger(...)`) for all logging — never `mu.KotlinLogging`
- Use `Schedulers.boundedElastic()` for all blocking SDK calls — never block the reactive event loop directly
- Commit after each phase checkpoint before proceeding
