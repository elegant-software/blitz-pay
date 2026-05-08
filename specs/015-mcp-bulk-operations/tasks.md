# Tasks: MCP Bulk Operations for Products and Categories

**Input**: Design documents from `specs/015-mcp-bulk-operations/`  
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓

**Tests**: Required — CONSTITUTION.md mandates tests for every behavior change.

**Note on User Story 3**: US3 (catalog re-import / idempotent handling) introduces no additional
implementation work. It is fully covered by the partial-success semantics added in US1
(`skipped` list with `existingId`). Verification is part of US1's test phase.

---

## Phase 1: Setup (Shared API Types)

**Purpose**: Add the response-envelope types shared by both bulk tools. Must complete before
the tool implementations in US1/US2.

- [X] T001 Add `BulkSkippedItem`, `BulkFailedItem`, `BulkProductInput`, and `BulkProductCreateResult` data classes to `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantProductModels.kt`
- [X] T002 [P] Add `BulkCategoryInput` and `BulkCategoryCreateResult` data classes to `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantProductCategoryModels.kt`

**Checkpoint**: Both files compile cleanly with `./gradlew compileKotlin`.

---

## Phase 2: Foundational (Prerequisite Fix)

**Purpose**: Fix the existing `product_id_by_name_or_create` tool to forward `categoryId`.
This is a precondition fix — the column and service layer already support it; the MCP tool
parameter was simply missing. Must pass tests before US1 work begins.

- [X] T003 Add `categoryId: String? = null` parameter to `getOrCreateProductId` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductTool.kt`; parse it as a UUID and pass it to `CreateProductRequest(categoryId = ...)`
- [X] T004 Add unit test `getOrCreateProductId passes categoryId into create request` to `src/test/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductToolsTest.kt`; verify `CreateProductRequest.categoryId` equals the passed UUID

**Checkpoint**: `./gradlew test --tests "*.MerchantProductToolsTest"` passes.

---

## Phase 3: User Story 1 — Bulk Product Creation (Priority: P1) 🎯 MVP

**Goal**: An AI agent can create an entire product catalog (up to 200 items) in a single
`products_bulk_create` call. Valid items are created; existing items are skipped with their
IDs; invalid items are reported with reasons. US3 re-import scenario is covered here.

**Independent Test**: Call `products_bulk_create` with a list containing a new product,
a duplicate product name, and a product with a negative price. Verify `created` has 1 item,
`skipped` has 1 item, and `failed` has 1 item.

### Implementation

- [X] T005 [US1] Implement `bulkCreateProducts` `@McpTool` method in `src/main/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductTool.kt`:
  - Guard: `require(products.size <= 200)`
  - Parse `merchantId` and `branchId` as UUIDs (reject entire request on parse failure)
  - For each item: track seen names in a `LinkedHashSet` (case-insensitive); if duplicate within batch → add to `skipped` with `reason = "duplicate within batch"`
  - Call `merchantProductService.findByNameIncludingInactive(mId, bId, name)` — if found → add to `skipped` with `reason = "already exists"` and `existingId`
  - Call `merchantProductService.create(...)` in `runCatching` — on success → `created`; on failure → `failed` with `ex.message`
  - Return `BulkProductCreateResult(created, skipped, failed)`

### Tests

- [X] T006 [US1] Add the following unit tests to `src/test/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductToolsTest.kt`:
  - `bulkCreateProducts creates all items when none exist` — 3 items, all succeed, `created.size == 3`
  - `bulkCreateProducts skips products that already exist by name` — 1 existing + 1 new; `skipped.size == 1` with `existingId` set, `created.size == 1`
  - `bulkCreateProducts skips within-batch name duplicates` — same name twice; second → `skipped` with `reason = "duplicate within batch"`
  - `bulkCreateProducts rejects entire batch when size exceeds 200` — 201 items → `IllegalArgumentException` before any service call
  - `bulkCreateProducts puts item in failed list when service throws` — service throws `IllegalArgumentException`; item → `failed`; other items still created
  - `bulkCreateProducts passes categoryId through to create request` — `input.categoryId` set; verify `CreateProductRequest.categoryId` equals parsed UUID

**Checkpoint**: `./gradlew test --tests "*.MerchantProductToolsTest"` green. US1 and US3 fully covered.

---

## Phase 4: User Story 2 — Bulk Category Creation (Priority: P2)

**Goal**: An AI agent can create the full category structure for a merchant in a single
`categories_bulk_create` call before loading products. Existing categories are skipped with
their IDs; within-batch duplicates are caught before hitting the service.

**Independent Test**: Call `categories_bulk_create` with `["Starters", "Mains", "Starters"]`.
Verify `created` has 2 items ("Starters" and "Mains"), and `skipped` has 1 item with
`reason = "duplicate within batch"`.

### Implementation

- [X] T007 [US2] Implement `bulkCreateCategories` `@McpTool` method in `src/main/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductTool.kt`:
  - Guard: `require(categories.size <= 200)`
  - Parse `merchantId` as UUID (reject entire request on parse failure)
  - For each item: track seen names in a `LinkedHashSet` (case-insensitive); if duplicate within batch → `skipped` with `reason = "duplicate within batch"`
  - Call `merchantProductCategoryService.findByName(mId, name)` — if found → `skipped` with `reason = "already exists"` and `existingId`
  - Call `merchantProductCategoryService.create(mId, CreateProductCategoryRequest(name))` in `runCatching` — on success → `created`; on failure → `failed`
  - Return `BulkCategoryCreateResult(created, skipped, failed)`

### Tests

- [X] T008 [US2] Add the following unit tests to `src/test/kotlin/com/elegant/software/blitzpay/merchant/mcp/MerchantProductToolsTest.kt`:
  - `bulkCreateCategories creates all when none exist` — 3 categories; `created.size == 3`
  - `bulkCreateCategories skips categories that already exist for merchant` — 1 existing + 1 new; `skipped.size == 1` with `existingId` set
  - `bulkCreateCategories skips within-batch duplicates case-insensitively` — "Starters" and "starters"; second → `skipped` with `reason = "duplicate within batch"`
  - `bulkCreateCategories rejects entire batch when size exceeds 200` — 201 items → `IllegalArgumentException`
  - `bulkCreateCategories puts item in failed list when service throws` — service throws; item → `failed`; other items proceed

**Checkpoint**: `./gradlew test --tests "*.MerchantProductToolsTest"` green. US1 and US2 both pass independently.

---

## Phase 5: Polish & Verification

**Purpose**: End-to-end verification and documentation update required by CONSTITUTION.md.

- [X] T009 Run `./gradlew check` (unit tests + contract tests) and confirm green
- [X] T010 [P] Update `reference/mcp-server-best-practices.md` to list `products_bulk_create` and `categories_bulk_create` as documented bulk tools with a pointer to `specs/015-mcp-bulk-operations/contracts/mcp-tools.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational fix)**: Depends on Phase 1 completion (T001 must exist before T003 can use `BulkProductInput`)
- **Phase 3 (US1)**: Depends on Phase 1 + Phase 2 completion
- **Phase 4 (US2)**: Depends on Phase 1 completion only — independent of US1
- **Phase 5 (Polish)**: Depends on all prior phases complete

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 1 + Phase 2; independent of US2
- **US2 (P2)**: Depends on Phase 1 only; independent of US1
- **US3 (P3)**: No implementation tasks — covered by US1 semantics

### Within Each Phase

- T001 and T002 can run in parallel (different files)
- T003 must precede T004 (test exercises the fixed code)
- T005 must precede T006 (tests exercise the new tool)
- T007 must precede T008 (tests exercise the new tool)
- T009 must follow T006 and T008

---

## Parallel Opportunities

```
# Phase 1 — launch both together:
Task T001: Add bulk product types to MerchantProductModels.kt
Task T002: Add bulk category types to MerchantProductCategoryModels.kt

# After Phase 1 — US2 can start while US1 is being implemented:
Task T007: Implement bulkCreateCategories  [after T001]
Task T005: Implement bulkCreateProducts    [after T001 + T002 + T003]
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Complete Phase 1: T001, T002
2. Complete Phase 2: T003, T004
3. Complete Phase 3: T005, T006
4. **STOP and VALIDATE**: `./gradlew test --tests "*.MerchantProductToolsTest"`
5. Demo `products_bulk_create` with a 10-item catalog

### Incremental Delivery

1. Phase 1 + Phase 2 + Phase 3 → `products_bulk_create` live (MVP)
2. Phase 4 → `categories_bulk_create` live
3. Phase 5 → verified and documented

---

## Notes

- All new types are in `merchant/api/` per module packaging convention
- All tool methods are in `merchant/mcp/MerchantProductTool.kt` — no new files needed
- No Liquibase migrations — `product_category_id` column already exists
- Each `@Transactional` service call runs in its own DB transaction, giving natural per-item isolation for partial success
- `./gradlew check` must be green before the feature is considered done (CONSTITUTION requirement)
