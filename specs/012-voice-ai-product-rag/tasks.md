# Tasks: Voice AI Product Assistant

**Input**: Design documents from `specs/012-voice-ai-product-rag/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

**Organization**: Grouped by user story — each phase is independently deployable and testable.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no cross-dependency)
- **[Story]**: Which user story the task belongs to
- Include exact file paths in all descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add Ollama dependency and configuration so the Spring context can wire the chat client.

- [x] T001 Add `spring-ai-starter-model-ollama` to `build.gradle.kts` under the `implementation` block (alongside the existing `spring-ai-starter-mcp-server` entries)
- [x] T002 Add Ollama config block to `src/main/resources/application.yml` under `spring.ai.ollama` (`base-url: ${OLLAMA_BASE_URL:http://localhost:11434}`, `chat.model: ${OLLAMA_MODEL:llama3.2}`, `chat.options.temperature: 0.1`, `chat.options.num-predict: 256`)
- [x] T003 [P] Add `Ollama` nested data class to `src/main/kotlin/com/elegant/software/blitzpay/voice/config/VoiceProperties.kt` (`timeoutMs: Long = 4000`) and add `ollama: Ollama = Ollama()` field to `VoiceProperties`

**Checkpoint**: `./gradlew build` compiles without errors — Ollama starter is on the classpath. ✅

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core types and module boundaries that ALL user stories depend on. Nothing in Phase 3+ can be implemented without these.

**⚠️ CRITICAL**: Complete this phase before ANY user story work begins.

- [x] T004 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/CatalogSearchModels.kt` — define `data class CatalogProduct(val productId: UUID, val branchId: UUID, val name: String, val description: String?, val unitPrice: BigDecimal, val imageUrl: String?)`
- [x] T005 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantProductCatalogGateway.kt` — interface exposed via the existing `MerchantGateway` named interface in `merchant.api`
- [x] T006 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantProductCatalogService.kt` — `@Service` implementing `MerchantProductCatalogGateway`; delegates to existing `MerchantProductService.list(merchantId, branchId)`
- [x] T007 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/voice/api/AssistantResponseModels.kt` — sealed class `AssistantResponse` with Jackson `@JsonTypeInfo` / `@JsonSubTypes`; subtypes: `Transcript`, `ProductResult`, `NoMatch`; also defines `data class ProductMatch`
- [x] T008 Update `src/main/kotlin/com/elegant/software/blitzpay/voice/api/VoiceGateway.kt` — change `process()` return type from `VoiceTranscriptionResponse` to `AssistantResponse`
- [x] T009 Update `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceModels.kt` — add `merchantId: UUID?` and `branchId: UUID?` to `VoiceAudioSubmission`; add `data class ProductIntent`; remove `VoiceTranscriptionResponse`
- [x] T010 [P] Update `src/main/kotlin/com/elegant/software/blitzpay/voice/config/VoiceConfiguration.kt` — add `@Bean fun ollamaChatClient(ollamaChatModel: OllamaChatModel): ChatClient`
- [x] T011 Update `src/contractTest/kotlin/com/elegant/software/blitzpay/voice/VoiceQueryControllerContractTest.kt` — update stubs to return `AssistantResponse.Transcript`; add `type=TRANSCRIPT` assertion; add `PRODUCT_RESULT` and `NO_MATCH` contract tests

**Checkpoint**: `./gradlew check` passes — all existing contract tests still green with the new return type. ✅

---

## Phase 3: User Story 1 — Voice-Driven Product Selection (Priority: P1) 🎯 MVP

**Goal**: Customer names a product; assistant returns a `PRODUCT_RESULT` card with name, price, description, image, and `requestedQuantity=1` within 5 seconds.

**Independent Test**: POST `/v1/voice/query` with audio (stubbed to produce "I would like one Erdbeer Becher, please"), `merchantId`, and `branchId` → response `type=PRODUCT_RESULT`, `products[0].name="Erdbeer Becher"`, `requestedQuantity=1`.

- [x] T012 [P] [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/ProductIntentExtractor.kt` — `@Component` accepting `ChatClient`; sends catalog as JSON context to Ollama; parses `{"matches":[...],"quantity":N}` response into `ProductIntent`; throws `UpstreamAiException` on failure
- [x] T013 [P] [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/ProductCatalogSearch.kt` — `@Component`; maps `intent.matchedProductIds` to `ProductMatch` objects; returns `AssistantResponse.ProductResult` or `AssistantResponse.NoMatch`
- [x] T014 [US1] Update `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryService.kt` — orchestrate transcript → catalog load → intent extraction → search → response
- [x] T015 [US1] Update `src/main/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryController.kt` — add optional `merchantId`/`branchId` multipart fields; return `Mono<ResponseEntity<AssistantResponse>>`
- [x] T016 [US1] Add `PRODUCT_RESULT` contract test to `VoiceQueryControllerContractTest.kt` (done as part of T011)
- [x] T017 [P] [US1] Create `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/ProductIntentExtractorTest.kt` — unit tests for intent extraction, multi-match, 5-cap, empty matches, Ollama failures
- [x] T018 [P] [US1] Update `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryServiceTest.kt` — unit tests for transcript-only path, product match path, no-match path

**Checkpoint**: `./gradlew check` passes. ✅

---

## Phase 4: User Story 2 — Ambiguous or Partial Product Match (Priority: P2)

**Goal**: Partial/ambiguous voice query returns a ranked list of up to 5 candidates in `PRODUCT_RESULT`.

- [x] T019 [US2] `ProductIntentExtractor.kt` already handles multi-match with 5-cap — verified ✅
- [x] T020 [US2] `ProductCatalogSearch.kt` already maps all matched IDs, silently drops unresolved — verified ✅
- [x] T021 [US2] Multi-match contract test added to `VoiceQueryControllerContractTest.kt` ✅
- [x] T022 [P] [US2] Multi-match unit tests added to `ProductIntentExtractorTest.kt` ✅

**Checkpoint**: `./gradlew check` passes. ✅

---

## Phase 5: User Story 3 — No Match Graceful Response (Priority: P3)

**Goal**: Unrecognized request or no catalog match returns `NO_MATCH` with a friendly message — no product card.

- [x] T023 [US3] `ProductIntentExtractor.kt` correctly handles `{"matches":[],"quantity":null}` → empty `ProductIntent` ✅
- [x] T024 [US3] `ProductCatalogSearch.kt` returns `AssistantResponse.NoMatch("I didn't understand that request — try browsing the product screen.")` when matches empty ✅
- [x] T025 [US3] `NO_MATCH` contract test added to `VoiceQueryControllerContractTest.kt` ✅
- [x] T026 [P] [US3] No-match path covered by `VoiceQueryServiceTest.kt` ✅

**Checkpoint**: `./gradlew check` passes. All three response types covered. ✅

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T027 `./gradlew check` — BUILD SUCCESSFUL ✅
- [x] T028 [P] Modulith verification covered by existing `ModularityTests.verifiesArchitecture()` — `merchant.api` is already a named interface; voice module depends on it correctly
- [x] T029 [P] `helm/blitz-pay/templates/configmap.yaml`, `values.yaml`, `values-staging.yaml` updated with `OLLAMA_BASE_URL` and `OLLAMA_MODEL` ✅
- [x] T030 [P] `src/main/resources/application-local.yml` updated with local Ollama config ✅

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **blocks all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 — no dependency on US2/US3
- **US2 (Phase 4)**: Depends on Phase 2 — can run in parallel with US1 after Phase 2 completes
- **US3 (Phase 5)**: Depends on Phase 2 — same logic
- **Polish (Phase 6)**: Depends on all phases

### User Story Dependencies

| Story | Depends On | Independent? |
|-------|-----------|--------------|
| US1 (P1) | Phase 2 only | ✅ Yes |
| US2 (P2) | Phase 2 + US1 code (same files) | ✅ Testable independently |
| US3 (P3) | Phase 2 + US1 code (same files) | ✅ Testable independently |

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 30 |
| Completed | 30 ✅ |
| New files | 7 |
| Updated files | 9 |
| New env vars | `OLLAMA_BASE_URL`, `OLLAMA_MODEL` |
| `./gradlew check` | BUILD SUCCESSFUL ✅ |
