# Implementation Plan: MCP Bulk Operations for Products and Categories

**Branch**: `feat/014-keycloak-merchant-groups` | **Date**: 2026-05-09 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/015-mcp-bulk-operations/spec.md`

## Summary

Add two new MCP tools — `categories_bulk_create` and `products_bulk_create` — to the existing
`merchant` module, enabling an AI agent to populate an entire product catalog in a single call
rather than N individual round trips. Also fix the existing `product_id_by_name_or_create` tool
to forward `categoryId` to the create request (the column and service layer already support it;
only the tool parameter was missing). No schema changes are needed.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith 2.1.0-M3, Spring AI (`@McpTool`), Hibernate/JPA  
**Storage**: PostgreSQL 16 (`blitzpay` schema) — no new tables or columns  
**Testing**: JUnit 5 + Mockito Kotlin; contract tests with `WebTestClient` under `contract-test` profile  
**Target Platform**: JVM service  
**Performance Goals**: 200-item bulk create in < 5 s end-to-end  
**Constraints**: Partial-success semantics; max 200 items per call; no image upload in bulk path  
**Scale/Scope**: Single module (`merchant`); additive — no breaking changes to existing tools

## Constitution Check

| Gate | Status | Notes |
|------|--------|-------|
| Kotlin only, no Java source files | PASS | All new code is Kotlin |
| Spring Modulith module boundaries respected | PASS | All new types stay in `merchant/api/` and `merchant/mcp/`; no cross-module imports added |
| No cross-module table access | PASS | No new tables; all DB access via `merchant` module repositories |
| Liquibase owns schema — `ddl-auto: none` | PASS | No migrations needed |
| Contract tests for new/changed HTTP endpoints | N/A | Feature is MCP tools only (no new HTTP endpoints) |
| Every behavior change includes tests | REQUIRED | Unit tests in `MerchantProductToolsTest.kt` must cover all new and changed methods |
| `./gradlew check` passes before done | REQUIRED | Run before marking any task complete |
| No secrets or credentials in code | PASS | No new external integrations |
| `application` → `service` rename gap | NOTED | Existing inconsistency; not introduced by this feature — out of scope |

## Project Structure

### Documentation (this feature)

```text
specs/015-mcp-bulk-operations/
├── plan.md              ← this file
├── research.md          ← Phase 0
├── data-model.md        ← Phase 1
├── quickstart.md        ← Phase 1
├── contracts/
│   └── mcp-tools.md     ← Phase 1
└── tasks.md             ← Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── api/
│   ├── MerchantProductModels.kt          ← append BulkProductInput, BulkSkippedItem,
│   │                                        BulkFailedItem, BulkProductCreateResult
│   └── MerchantProductCategoryModels.kt  ← append BulkCategoryInput, BulkCategoryCreateResult
└── mcp/
    └── MerchantProductTool.kt            ← (1) fix getOrCreateProductId categoryId param
                                             (2) add bulkCreateCategories @McpTool
                                             (3) add bulkCreateProducts @McpTool

src/test/kotlin/com/elegant/software/blitzpay/merchant/
└── mcp/
    └── MerchantProductToolsTest.kt       ← extend with ~12 new test cases
```

**Structure Decision**: Single-module, additive changes only. No new packages. All new types co-located with their peers in the existing `api/` and `mcp/` sub-packages.

## Complexity Tracking

No constitution violations.
