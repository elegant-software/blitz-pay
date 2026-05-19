# Tasks: Product Price Savings Agent

**Input**: Design documents from `/Users/mehdi/MyProject/blitz-pay/specs/006-product-price-savings-agent/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/product-price-agent-contract.md`

**Tests**: Tests are required for this feature because the repository policy and current feature workflow already rely on unit, integration, and contract coverage for behavior changes.

**Organization**: Tasks are grouped by user story to keep KOOG orchestration, monitoring, and transport compatibility independently testable.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the KOOG runtime and prepare the feature docs/config for a KOOG-driven price research flow.

- [X] T001 Add KOOG dependency versions to `gradle/libs.versions.toml`
- [X] T002 Add KOOG runtime dependencies to `build.gradle.kts`
- [X] T003 [P] Document KOOG runtime configuration placeholders in `src/main/resources/application.yml`
- [X] T004 [P] Update feature quickstart references in `specs/006-product-price-savings-agent/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the shared KOOG orchestration foundation that all user stories depend on.

**⚠️ CRITICAL**: No user story work should begin until this phase is complete.

- [X] T005 Create KOOG request/session models in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/KoogAgentModels.kt`
- [X] T006 Create KOOG monitoring event mapper in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/KoogMonitoringMapper.kt`
- [X] T007 [P] Create market-search KOOG tool wrapper in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/tool/MarketSearchToolAdapter.kt`
- [X] T008 [P] Create price-comparison KOOG tool wrapper in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/tool/PriceComparisonToolAdapter.kt`
- [X] T009 Create KOOG tool registry factory in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchToolRegistryFactory.kt`
- [X] T010 Create KOOG prompt/system instructions for product research in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchSystemPrompt.kt`
- [X] T011 Create KOOG agent factory using `AIAgent` orchestration in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogAgentFactory.kt`
- [X] T012 Wire KOOG configuration beans in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/config/InvoiceAgentConfiguration.kt`
- [X] T013 Add foundational KOOG factory tests in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogAgentFactoryTest.kt`

**Checkpoint**: KOOG orchestration foundation exists and controllers can be redirected to it.

---

## Phase 3: User Story 1 - Find Better Price for a Product (Priority: P1) 🎯 MVP

**Goal**: A KOOG agent accepts a product-price request, performs market research through tools, and returns a better-price result when one exists.

**Independent Test**: Submit a valid request above market price and verify one KOOG agent run produces a structured `better_price_found` response with the best offer and exact savings.

### Tests for User Story 1

- [X] T014 [P] [US1] Add KOOG orchestration unit tests for successful tool flow in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogFlowTest.kt`
- [X] T015 [P] [US1] Add HTTP integration test for better-price responses through KOOG orchestration in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/api/InvoiceAgentTestControllerPriceComparisonTest.kt`
- [X] T016 [P] [US1] Add A2A integration test for KOOG-backed better-price responses in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/a2a/InvoiceA2aControllerPriceComparisonTest.kt`

### Implementation for User Story 1

- [X] T017 [P] [US1] Refine request/response DTO documentation for product-price research in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/api/PriceComparisonDtos.kt`
- [X] T018 [US1] Replace direct service orchestration with KOOG-run orchestration in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/application/InvoiceAgentService.kt`
- [X] T019 [US1] Update the HTTP controller to invoke the KOOG-backed service path in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/api/InvoiceAgentTestController.kt`
- [X] T020 [US1] Update the A2A controller to invoke the KOOG-backed service path in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/a2a/InvoiceA2aController.kt`
- [X] T021 [US1] Update the legacy invoice tool adapter or replace it with product-research semantics in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/tool/InvoiceToolAdapter.kt`
- [X] T022 [US1] Ensure market-search module entrypoints support KOOG tool usage in `src/main/kotlin/com/elegant/software/blitzpay/marketsearch/api/MarketSearchGateway.kt`
- [X] T023 [US1] Ensure price-comparison module entrypoints support KOOG tool usage in `src/main/kotlin/com/elegant/software/blitzpay/pricecomparison/api/PriceComparisonGateway.kt`
- [X] T024 [US1] Update OpenAPI descriptions to describe KOOG product research instead of invoice creation in `src/main/kotlin/com/elegant/software/blitzpay/config/OpenApiConfig.kt`

**Checkpoint**: User Story 1 works end to end with KOOG-based better-price research.

---

## Phase 4: User Story 2 - Confirm No Savings Available (Priority: P2)

**Goal**: The KOOG agent returns clear negative outcomes when no lower price exists or no comparable offers are available.

**Independent Test**: Submit requests that produce `no_better_price_found` and `comparison_unavailable` and verify KOOG orchestration returns the correct structured outcomes without persistence.

### Tests for User Story 2

- [X] T025 [P] [US2] Add KOOG negative-outcome unit tests in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogNegativeOutcomeTest.kt`
- [X] T026 [P] [US2] Add HTTP no-savings integration coverage in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/api/InvoiceAgentNoSavingsControllerTest.kt`
- [X] T027 [P] [US2] Add price-comparison outcome regression tests for KOOG-driven warnings/failures in `src/test/kotlin/com/elegant/software/blitzpay/pricecomparison/application/PriceComparisonServiceOutcomeTest.kt`

### Implementation for User Story 2

- [X] T028 [US2] Implement KOOG-agent mapping for `no_better_price_found` outcomes in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/application/InvoiceAgentService.kt`
- [X] T029 [US2] Implement KOOG-agent mapping for `comparison_unavailable` outcomes in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/application/InvoiceAgentService.kt`
- [X] T030 [US2] Refine provider warning propagation for partial results in `src/main/kotlin/com/elegant/software/blitzpay/marketsearch/application/MarketSearchService.kt`
- [X] T031 [US2] Refine outcome explanation mapping for KOOG-driven failures in `src/main/kotlin/com/elegant/software/blitzpay/pricecomparison/application/PriceComparisonOutcomeFactory.kt`

**Checkpoint**: Negative outcomes remain deterministic and independently testable through KOOG orchestration.

---

## Phase 5: User Story 4 - Monitor Active Request Progress (Priority: P2)

**Goal**: The API exposes per-request progress, bottlenecks, warnings, and failure details that reflect KOOG runtime execution.

**Independent Test**: Submit a request and verify the response includes KOOG-driven monitoring data for startup, search, extraction, comparison, and failure/slowdown conditions.

### Tests for User Story 4

- [X] T032 [P] [US4] Add KOOG monitoring mapper unit tests in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/KoogMonitoringMapperTest.kt`
- [X] T033 [P] [US4] Add HTTP monitoring integration coverage in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/api/InvoiceAgentMonitoringControllerTest.kt`
- [X] T034 [P] [US4] Add price-comparison monitoring regression coverage for KOOG failures in `src/test/kotlin/com/elegant/software/blitzpay/pricecomparison/application/PriceComparisonMonitoringTest.kt`
- [X] T035 [P] [US4] Add market-search monitoring regression coverage for KOOG-triggered provider activity in `src/test/kotlin/com/elegant/software/blitzpay/marketsearch/application/MarketSearchMonitoringTest.kt`

### Implementation for User Story 4

- [X] T036 [US4] Emit KOOG lifecycle progress events in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogAgentFactory.kt`
- [X] T037 [US4] Map KOOG tool-call failures and slowdowns into monitoring snapshots in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/KoogMonitoringMapper.kt`
- [X] T038 [US4] Surface KOOG monitoring details through the service response assembler in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/application/InvoiceAgentService.kt`
- [X] T039 [US4] Update Swagger-visible monitoring field descriptions for KOOG stages in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/api/PriceComparisonDtos.kt`

**Checkpoint**: Monitoring reflects KOOG execution without changing the public response shape.

---

## Phase 6: User Story 3 - Receive Machine-Readable Comparison Output (Priority: P3)

**Goal**: HTTP and A2A consumers continue receiving the same structured schema while KOOG becomes the internal orchestrator.

**Independent Test**: Call both HTTP and A2A flows and verify the structured response semantics remain stable with KOOG-backed execution.

### Tests for User Story 3

- [X] T040 [P] [US3] Add contract regression coverage for KOOG-backed transport semantics in `src/contractTest/kotlin/com/elegant/software/blitzpay/invoiceagent/PriceComparisonAgentContractTest.kt`
- [X] T041 [P] [US3] Add response-serialization regression tests for KOOG-backed payloads in `src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/application/PriceComparisonResultSerializationTest.kt`

### Implementation for User Story 3

- [X] T042 [US3] Update A2A card and capability descriptions to describe product-price research in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/a2a/InvoiceAgentCardFactory.kt`
- [X] T043 [US3] Update A2A property/config descriptions for the KOOG product research agent in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/config/InvoiceAgentProperties.kt`
- [X] T044 [US3] Keep HTTP DTO and A2A payload structure aligned after KOOG orchestration changes in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/a2a/A2aModels.kt`
- [X] T045 [US3] Update feature contract examples to reflect KOOG-backed orchestration in `specs/006-product-price-savings-agent/contracts/product-price-agent-contract.md`

**Checkpoint**: Structured outputs remain stable across transports.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Finish naming, docs, and verification around the KOOG-only product research scope.

- [X] T046 [P] Rename or de-emphasize invoice-only prompt text in `src/main/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/InvoiceAgentSystemPrompt.kt`
- [X] T047 [P] Update feature planning docs for the KOOG-only agent scope in `specs/006-product-price-savings-agent/plan.md`
- [X] T048 [P] Update repository agent context using `./.specify/scripts/bash/update-agent-context.sh codex` in `/Users/mehdi/MyProject/blitz-pay/AGENTS.md`
- [X] T049 Run `./gradlew test` and `./gradlew contractTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: starts immediately.
- **Phase 2: Foundational**: depends on Phase 1 and blocks all user stories.
- **Phase 3: US1**: starts after Phase 2.
- **Phase 4: US2**: starts after Phase 2 and depends on US1 service semantics for outcome mapping.
- **Phase 5: US4**: starts after Phase 2 and should be completed before transport polish so KOOG monitoring is stable.
- **Phase 6: US3**: starts after Phase 2 and is best finalized after US1/US2/US4 semantics are stable.
- **Phase 7: Polish**: depends on all selected stories.

### User Story Dependencies

- **US1**: foundational MVP, no dependency on other stories after Phase 2.
- **US2**: depends on the same KOOG orchestration path as US1.
- **US4**: depends on KOOG orchestration foundation and should land before final transport polish.
- **US3**: depends on KOOG orchestration and monitoring semantics already being in place.

### Parallel Opportunities

- T003 and T004 can run in parallel.
- T007 and T008 can run in parallel.
- US1 test tasks T014-T016 can run in parallel.
- US2 test tasks T025-T027 can run in parallel.
- US4 test tasks T032-T035 can run in parallel.
- US3 test tasks T040-T041 can run in parallel.

## Parallel Example: User Story 1

```bash
Task: "Add KOOG orchestration unit tests for successful tool flow in src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/koog/ProductResearchKoogFlowTest.kt"
Task: "Add HTTP integration test for better-price responses through KOOG orchestration in src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/api/InvoiceAgentTestControllerPriceComparisonTest.kt"
Task: "Add A2A integration test for KOOG-backed better-price responses in src/test/kotlin/com/elegant/software/blitzpay/invoiceagent/a2a/InvoiceA2aControllerPriceComparisonTest.kt"
```

## Implementation Strategy

### MVP First

1. Complete Phase 1 and Phase 2.
2. Complete US1 and verify KOOG-backed better-price research end to end.
3. Stop and validate before expanding to negative outcomes and monitoring refinement.

### Incremental Delivery

1. KOOG foundation
2. KOOG-backed positive result flow
3. KOOG-backed negative outcomes
4. KOOG-backed monitoring
5. Contract/transport polish

### Notes

- Keep the external API stable while replacing orchestration internals.
- Keep market-search and price-comparison logic outside the prompt and behind tools.
- Do not add persistence or invoice-creation behavior.
