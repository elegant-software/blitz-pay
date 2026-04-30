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

**Independent Test**: `POST /v1/payments/stripe/create-intent` with `{"amount": 12.50}` returns a JSON body containing `paymentRequestId` (UUID), `clientSecret` (non-blank string), `paymentIntent` (non-blank string), and `publishableKey` (non-blank string). Invalid amount returns HTTP 400.

### Implementation for User Story 1

- [x] T007 [P] [US1] Create `src/main/kotlin/.../payments/stripe/package-info.kt` declaring `@ApplicationModule` with `allowedDependencies = []` (no cross-module dependencies needed)
- [x] T008 [P] [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeProperties.kt` as `@ConfigurationProperties(prefix = "stripe")` with `secretKey: String` and `publishableKey: String` fields
- [x] T009 [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeConfig.kt` with a `@Bean` that sets `Stripe.apiKey = properties.secretKey` on application startup (Stripe SDK uses a static API key)
- [x] T010 [P] [US1] Create `src/main/kotlin/.../payments/stripe/config/StripeOpenApiConfig.kt` registering a `GroupedOpenApi` bean named `"Stripe"` for paths `/v1/payments/stripe/**`
- [x] T011 [US1] Create `src/main/kotlin/.../payments/stripe/internal/StripePaymentService.kt` with a `createIntent(amount: Double, currency: String): StripeIntentResult` method that calls `PaymentIntents.create(...)` wrapped in `Mono.fromCallable { }.subscribeOn(Schedulers.boundedElastic())`, validates `amount > 0`, and returns `client_secret` + publishable key
- [x] T012 [US1] Create `src/main/kotlin/.../payments/stripe/internal/StripePaymentController.kt` mapping `POST /v1/payments/stripe/create-intent`, accepts `{"amount": Double, "currency": String?}`, delegates to `StripePaymentService`, returns `{"paymentRequestId": ..., "clientSecret": ..., "paymentIntent": ..., "publishableKey": ...}` on success or `{"error": ...}` with HTTP 400/500
- [x] T013 [P] [US1] Create `src/test/kotlin/.../payments/stripe/StripePaymentServiceTest.kt` with unit tests covering: happy path returns client secret, zero amount throws validation error, negative amount throws validation error, Stripe API exception maps to 500 response
- [x] T014 [P] [US1] Create `src/contractTest/kotlin/.../payments/stripe/StripePaymentControllerContractTest.kt` with `WebTestClient` contract tests: valid amount → 200 with `paymentRequestId`, `clientSecret`, `paymentIntent`, and `publishableKey` fields present; missing amount → 400; invalid amount (zero) → 400
- [x] T015 [US1] Update `src/test/kotlin/.../quickpay/payments/ModularityTest.kt.kt` to add `payments.stripe` to the `ApplicationModules.of(...)` verification so module boundary violations are caught

**Checkpoint**: `./gradlew check` passes. Smoke test from quickstart.md Stripe section succeeds against a real sandbox key.

---

## Phase 4: User Story 2 — PayPal / Digital Wallet via Braintree (Priority: P2)

**Goal**: Mobile clients can fetch a Braintree client token and submit a payment nonce for settlement. When Braintree is unconfigured, both endpoints return HTTP 503.

**Independent Test**: (1) `POST /v1/payments/braintree/client-token` returns `{"clientToken": "<non-blank>"}`. (2) `POST /v1/payments/braintree/checkout` with a sandbox nonce and `amount: 12.50` returns `{"status": "succeeded", "paymentRequestId": "<uuid>", "transactionId": "<non-blank>", ...}`. (3) Both endpoints return `{"error": "Braintree not configured on server"}` with HTTP 503 when env vars are absent.

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

## Phase 7: Merchant Domain Extension (Foundational for merchant/branch routing)

**Purpose**: Introduce `MerchantBranch` entity, add payment credential columns to `MerchantApplication`, expose `MerchantCredentialResolver` as a `@NamedInterface` so payment modules can resolve per-branch credentials without violating Modulith boundaries.

**⚠️ CRITICAL**: Phases 8, 9, 10 all depend on this phase being complete.

- [x] T032 Add nullable Stripe credential columns (`stripeSecretKey: String?`, `stripePublishableKey: String?`) and nullable Braintree credential columns (`braintreeMerchantId: String?`, `braintreePublicKey: String?`, `braintreePrivateKey: String?`, `braintreeEnvironment: String?`) to `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantApplication.kt` (6 new `@Column` fields, all nullable)
- [x] T033 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantBranch.kt` as a `@Entity @Table(name = "merchant_branches", schema = "blitzpay")` with fields: `id: UUID`, `merchantApplicationId: UUID` (FK, immutable), `name: String`, `active: Boolean = true`, nullable Stripe and Braintree credential columns (same 6 as T032), `createdAt: Instant`, `updatedAt: Instant`; add index `idx_merchant_branches_merchant_application_id`
- [x] T034 [P] Add nullable `@Column(name = "merchant_branch_id") var merchantBranchId: UUID? = null` to `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/MerchantProduct.kt`
- [x] T035 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/repository/MerchantBranchRepository.kt` as `JpaRepository<MerchantBranch, UUID>` with `findAllByMerchantApplicationIdAndActiveTrue(merchantApplicationId: UUID)` and `findByIdAndActiveTrue(id: UUID)` query methods
- [x] T036 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantCredentialModels.kt` with `data class StripeCredentials(val secretKey: String, val publishableKey: String)` and `data class BraintreeCredentials(val merchantId: String, val publicKey: String, val privateKey: String, val environment: String)`
- [x] T037 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantBranchModels.kt` with `data class CreateBranchRequest(val name: String, val stripeSecretKey: String? = null, ...)` and `data class BranchResponse(val id: UUID, val merchantId: UUID, val name: String, val active: Boolean, val hasStripeCredentials: Boolean, val hasBraintreeCredentials: Boolean, val createdAt: Instant, val updatedAt: Instant)` — credential keys never returned
- [x] T038 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantCredentialResolver.kt` declaring interface with `resolveStripe(merchantId: UUID, branchId: UUID?): StripeCredentials?`, `resolveBraintree(merchantId: UUID, branchId: UUID?): BraintreeCredentials?`, `resolveBranch(merchantId: UUID, branchId: UUID?, productId: UUID?): UUID?`; update `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/package-info.kt` to add `@NamedInterface("MerchantCredentialResolver")` annotation
- [x] T039 Implement `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantCredentialResolverImpl.kt`: branch credentials override merchant defaults; if both absent return null; `resolveBranch` returns explicit `branchId` → `product.merchantBranchId` → null
- [x] T040 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantBranchService.kt` with `create(merchantId: UUID, request: CreateBranchRequest): BranchResponse`, `list(merchantId: UUID): List<BranchResponse>`, `get(merchantId: UUID, branchId: UUID): BranchResponse`; validate merchant exists (404 if not)
- [x] T041 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/web/MerchantBranchController.kt` mapping `POST /v1/merchants/{merchantId}/branches` (201 BranchResponse), `GET /v1/merchants/{merchantId}/branches` (200 List<BranchResponse>); error: 404 when merchant not found, 400 when name blank

**Checkpoint**: `./gradlew check` passes. `ApplicationModules.of(...).verify()` in `ModularityTest` still passes with `MerchantCredentialResolver` registered as `@NamedInterface`.

---

## Phase 8: US1 Update — Stripe with Credential Resolution & Provider Metadata

**Goal**: `POST /v1/payments/stripe/create-intent` now accepts `merchantId` (required), optional `branchId`, optional `productId`. Stripe PaymentIntent carries `merchantId`, `branchId`, `productId` in its `metadata` field. Credentials are resolved per-request via `RequestOptions`; the global `Stripe.apiKey` assignment is removed.

**Independent Test**: Request with `merchantId`, valid Stripe credentials configured on the merchant, returns PaymentIntent with metadata containing `merchantId`. Request without `merchantId` returns 400. No branch/product credentials → 503.

- [ ] T042 Remove `@PostConstruct fun configureStripe()` from `src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/config/StripeConfig.kt` so `Stripe.apiKey` is no longer set globally
- [ ] T043 [US1] Update `src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/internal/StripePaymentService.kt`: add `credentials: StripeCredentials`, `merchantId: UUID`, `branchId: UUID?`, `productId: UUID?` parameters to `createIntent()`; build `RequestOptions.builder().setApiKey(credentials.secretKey).build()`; call `PaymentIntent.create(params, requestOptions)`; add `.putMetadata("merchantId", ...)`, `.putMetadata("branchId", ...)`, `.putMetadata("productId", ...)` to `PaymentIntentCreateParams`; update log to include `merchantId`, `branchId`, `productId`
- [ ] T044 [US1] Update `src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/internal/StripePaymentController.kt`: extend `CreateIntentRequest` with `val merchantId: UUID? = null`, `val branchId: UUID? = null`, `val productId: UUID? = null`; inject `MerchantCredentialResolver`; implement resolution chain: validate `merchantId` → resolve branch (400 if null) → load product price if `productId` present → resolve `StripeCredentials` (503 if null) → call updated service; log WARN if explicit `amount` differs from `product.unitPrice`
- [ ] T045 [P] [US1] Update `src/test/kotlin/com/elegant/software/blitzpay/payments/stripe/StripePaymentServiceTest.kt`: add test cases verifying `RequestOptions` carries resolved secret key; verify metadata map contains `merchantId`, `branchId`, `productId` on the created `PaymentIntentCreateParams`
- [ ] T046 [P] [US1] Update `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/stripe/StripePaymentControllerContractTest.kt`: add `merchantId` to request bodies; mock `MerchantCredentialResolver` returning valid credentials; add 400 test for missing `merchantId`; add 400 test for unresolvable branch; add 503 test for null credentials

**Checkpoint**: `./gradlew check` passes. Stripe smoke test with `merchantId` in the request body returns a PaymentIntent.

---

## Phase 9: US2/US3 Update — Braintree with Credential Resolution & Provider Metadata

**Goal**: Both Braintree endpoints accept `merchantId` (required), optional `branchId`, optional `productId`. Transactions carry `orderId` (set to `productId ?: invoiceId`) and log `merchantId`/`branchId` for traceability. Credentials resolved per-request via a cached `BraintreeGatewayFactory`.

**Independent Test**: `POST /v1/payments/braintree/checkout` with `merchantId`, sandbox nonce, and `productId` returns success with `merchantId` and `branchId` in the response body. `orderId` on the Braintree transaction equals the supplied `productId`.

- [ ] T047 Remove the `@Bean @ConditionalOnProperty` `BraintreeGateway` bean from `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/config/BraintreeConfig.kt`
- [ ] T048 [P] [US2] Create `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/internal/BraintreeGatewayFactory.kt` with `ConcurrentHashMap<String, BraintreeGateway>` cache keyed on `"${credentials.merchantId}:${credentials.publicKey}:${credentials.environment}"`; `fun get(credentials: BraintreeCredentials): BraintreeGateway`
- [ ] T049 [US2] Update `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/internal/BraintreePaymentService.kt`: replace `Optional<BraintreeGateway>` with `BraintreeGatewayFactory`; add `credentials: BraintreeCredentials`, `merchantId: UUID`, `branchId: UUID?`, `productId: UUID?` params to `generateClientToken()` and `checkout()`; set `TransactionRequest.orderId(productId?.toString() ?: invoiceId ?: "")`; add `merchantId`/`branchId` to `BraintreeCheckoutResult.Success`; update log lines
- [ ] T050 [US2] Update `src/main/kotlin/com/elegant/software/blitzpay/payments/braintree/internal/BraintreePaymentController.kt`: extend `ClientTokenRequest` with `merchantId: UUID?`, `branchId: UUID?`; extend `CheckoutRequest` with `merchantId: UUID?`, `branchId: UUID?`, `productId: UUID?`; inject `MerchantCredentialResolver`; implement same resolution chain as Stripe controller (T044); extend `CheckoutSuccessResponse` with `merchantId: UUID`, `branchId: UUID?`
- [ ] T051 [P] [US2] Update `src/test/kotlin/com/elegant/software/blitzpay/payments/braintree/BraintreePaymentServiceTest.kt`: add tests verifying factory is called with resolved credentials; verify `orderId` is set to `productId` when present; verify `BraintreeCheckoutResult.Success` carries `merchantId`/`branchId`
- [ ] T052 [P] [US2] Create `src/test/kotlin/com/elegant/software/blitzpay/payments/braintree/BraintreeGatewayFactoryTest.kt`: same fingerprint → same cached `BraintreeGateway` instance; different fingerprint → different instance
- [ ] T053 [P] [US2] Update `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/braintree/BraintreePaymentControllerContractTest.kt`: add `merchantId` to all request bodies; mock `MerchantCredentialResolver`; add 400 test for missing `merchantId`; add 503 test for null credentials; verify success response contains `merchantId`/`branchId`

**Checkpoint**: `./gradlew check` passes. Braintree sandbox smoke test with `merchantId` succeeds and response includes `merchantId`.

---

## Phase 10: TrueLayer Metadata Extension

**Goal**: `PaymentRequested` gains optional `merchantId`, `branchId`, `productId` fields. `PaymentService` includes non-null values in the `.metadata()` map already sent to TrueLayer on every payment creation.

**Independent Test**: A `PaymentRequested` event with `merchantId` set produces a TrueLayer `CreatePaymentRequest` whose metadata map contains `"merchantId"`. Events without these fields produce the same metadata as today (no regression).

- [ ] T054 [P] Add `val merchantId: UUID? = null`, `val branchId: UUID? = null`, `val productId: UUID? = null` optional fields to `PaymentRequested` in `src/main/kotlin/com/elegant/software/blitzpay/payments/truelayer/api/PaymentGateway.kt` (default null preserves backward compat with all existing callers)
- [ ] T055 Update `src/main/kotlin/com/elegant/software/blitzpay/payments/truelayer/outbound/PaymentService.kt`: replace the existing `mapOf(...)` in `.metadata(...)` with `buildMap { put("paymentRequestId", ...); put("orderId", ...); paymentRequest.merchantId?.let { put("merchantId", it.toString()) }; paymentRequest.branchId?.let { put("branchId", it.toString()) }; paymentRequest.productId?.let { put("productId", it.toString()) } }`; update the `log.info` line to include `merchantId`, `branchId`, `productId`

**Checkpoint**: `./gradlew check` passes. Existing `PaymentRequested` usages compile unchanged (all new fields have defaults).

---

## Phase 11: Testing & Final Verification

- [ ] T056 [P] Create `src/test/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantCredentialResolverImplTest.kt`: branch credentials override merchant defaults; merchant defaults used when branch has no credentials; null returned when both absent; `resolveBranch` returns explicit `branchId`; `resolveBranch` returns `product.merchantBranchId` when `productId` supplied; `resolveBranch` returns null when neither provided
- [ ] T057 [P] Create `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantBranchControllerContractTest.kt`: `POST /v1/merchants/{id}/branches` 201 with valid request; 400 with blank name; 404 with unknown `merchantId`; `GET /v1/merchants/{id}/branches` 200 list; secret keys not present in any response field
- [ ] T058 [P] Add 400/503 edge case contract tests to `src/contractTest/kotlin/com/elegant/software/blitzpay/payments/stripe/StripePaymentControllerContractTest.kt` and `BraintreePaymentControllerContractTest.kt` for: branch unresolvable (400), no credentials at branch or merchant level (503)
- [ ] T059 Run `./gradlew test --tests "*.ModularityTest"` and `./gradlew test --tests "*.MerchantModularityTest"` to confirm `ApplicationModules.verify()` passes with `MerchantCredentialResolver` as `@NamedInterface` consumed by `payments.stripe` and `payments.braintree`
- [ ] T060 Update `specs/007-stripe-braintree-payment-apis/quickstart.md`: add `merchantId`/`branchId` to all three payment endpoint smoke test examples; add a `POST /v1/merchants/{id}/branches` example showing branch creation with Stripe/Braintree credential override

**Checkpoint**: `./gradlew check` fully green. All smoke tests in updated quickstart.md succeed against sandbox.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately ✅ done
- **Phase 2 (Foundational)**: Requires Phase 1 ✅ done
- **Phase 3 (US1 Stripe basic)**: Requires Phase 2 ✅ done
- **Phase 4 (US2 Braintree basic)**: Requires Phase 2 ✅ done
- **Phase 5 (US3 Invoice basic)**: Requires Phase 4 ✅ done
- **Phase 6 (Polish basic)**: ✅ done (T031 outstanding)
- **Phase 7 (Merchant Domain)**: No new setup deps — BLOCKS Phases 8, 9, 10
- **Phase 8 (US1 Stripe + credentials/metadata)**: Requires Phase 7
- **Phase 9 (US2/US3 Braintree + credentials/metadata)**: Requires Phase 7
- **Phase 10 (TrueLayer metadata)**: Independent of Phases 8/9 — requires Phase 7 only for `PaymentRequested` fields (or can run in parallel)
- **Phase 11 (Testing & verification)**: Requires Phases 8, 9, 10

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
