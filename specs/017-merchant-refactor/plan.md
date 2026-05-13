# Implementation Plan: Merchant Refactor

**Branch**: `017-merchant-refactor` | **Date**: 2026-05-14 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-merchant-refactor/spec.md`

## Summary

Refactor the merchant module from onboarding-flavoured language into an operational merchant model, rename business identity and foreign-key surfaces accordingly, introduce merchant-wide offering governance, and align order validation with explicit branch ownership and order type rules. The implementation keeps Spring Modulith boundaries intact, performs schema evolution through Liquibase changesets, and updates HTTP contracts and tests so operational merchant, branch, and order behaviour remain consistent after migration.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, Jackson Kotlin module, Bean Validation  
**Storage**: PostgreSQL 16 in schema `blitzpay`  
**Testing**: JUnit 5, Mockito Kotlin, WebTestClient contract tests, Spring Modulith `ApplicationModules.verify()` checks  
**Target Platform**: JVM web service on Linux/server deployment  
**Project Type**: Spring Modulith web service  
**Performance Goals**: Preserve interactive merchant and order flows within existing user-facing request budgets; keep order validation in the same request path as current order creation flows  
**Constraints**: Kotlin-only application code; URL-versioned HTTP contracts; Liquibase-only schema changes with rollback; table ownership stays within the `merchant` and `order` modules; cross-module access via `api` surfaces instead of repository coupling; every changed behaviour requires unit and contract coverage  
**Scale/Scope**: Refactor the existing merchant parent aggregate, merchant-branch linkage, offering governance, and branch-linked order validation across the merchant and order modules without redesigning appointment workflow

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The `.specify/memory/constitution.md` file is still a placeholder, so enforceable gates are taken from the repository `CONSTITUTION.md`, which AGENTS.md marks as governing policy.

### Pre-Research Gate

- `PASS`: Kotlin-only implementation remains required; no Java source introduction is planned.
- `PASS`: Spring Modulith boundaries remain intact; order must continue consuming merchant data through `merchant.api` instead of direct repository access.
- `PASS`: HTTP changes remain on `/v1/...` paths and must be backed by contract tests in `src/contractTest/kotlin`.
- `PASS`: Schema changes will be delivered exclusively through Liquibase changesets in `src/main/resources/db/changelog/` with rollback directives.
- `PASS`: Table ownership stays within leaf-module prefixes (`merchant_*`, `order_*`) and avoids new cross-module table access.
- `PASS`: Documentation updates are part of scope because API contracts, module boundaries, and domain language are changing.

### Post-Design Gate

- `PASS`: Design keeps cross-module communication through `MerchantGateway` and related `api` contracts rather than direct bean or repository coupling.
- `PASS`: Design introduces contract artifacts for merchant operations and order validation changes, which map cleanly to required contract-test updates.
- `PASS`: Design keeps schema work within Liquibase and preserves module table ownership while renaming onboarding-flavoured merchant surfaces.
- `PASS`: No constitution violations require special justification.

## Project Structure

### Documentation (this feature)

```text
specs/017-merchant-refactor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── merchant-operations.md
│   └── order-validation.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── kotlin/com/elegant/software/blitzpay/
│   │   ├── merchant/
│   │   │   ├── api/
│   │   │   ├── config/
│   │   │   ├── iam/
│   │   │   ├── mcp/
│   │   │   ├── persistence/model/
│   │   │   ├── persistence/repository/
│   │   │   ├── service/
│   │   │   └── web/
│   │   └── order/
│   │       ├── api/
│   │       ├── config/
│   │       ├── persistence/model/
│   │       ├── persistence/repository/
│   │       ├── service/
│   │       └── web/
│   └── resources/db/changelog/
├── test/kotlin/com/elegant/software/blitzpay/
│   ├── merchant/
│   └── order/
└── contractTest/kotlin/com/elegant/software/blitzpay/
    ├── merchant/
    └── order/

api-docs/
└── api-doc.yml
```

**Structure Decision**: Keep the existing Spring Modulith structure and refactor inside the `merchant` and `order` modules rather than creating a new module. The primary changes land in merchant persistence/API/service/web layers, order API/service/web/persistence layers, Liquibase changelogs, contract tests, and generated API documentation.

## Phase 0 Output

- [research.md](./research.md): records the naming, migration, offering-catalogue, status-mapping, and order-type backfill decisions needed to remove ambiguity before implementation.

## Phase 1 Output

- [data-model.md](./data-model.md): defines the operational merchant, branch, merchant offering, and order shape after refactoring.
- [contracts/merchant-operations.md](./contracts/merchant-operations.md): captures merchant and branch contract changes.
- [contracts/order-validation.md](./contracts/order-validation.md): captures branch-linked order and offering-validation behaviour.
- [quickstart.md](./quickstart.md): defines the validation path for merchant creation, branch requirements, offering rules, and order-type enforcement.

## Complexity Tracking

No constitution exceptions or complexity waivers are required at planning time.
