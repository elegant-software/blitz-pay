# Tasks: Merchant Refactor

**Input**: Design documents from `/specs/017-merchant-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are included because the repository constitution requires every behavior change to add or update passing tests, including contract and modulith verification where applicable.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the feature skeleton and document references used by implementation.

- [X] T001 Create the feature task list in `/Users/mehdi/MyProject/blitz-pay/specs/017-merchant-refactor/tasks.md`
- [X] T002 Review and reconcile merchant/order contract references in `/Users/mehdi/MyProject/blitz-pay/specs/017-merchant-refactor/plan.md`, `/Users/mehdi/MyProject/blitz-pay/specs/017-merchant-refactor/contracts/merchant-operations.md`, and `/Users/mehdi/MyProject/blitz-pay/specs/017-merchant-refactor/contracts/order-validation.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core refactor infrastructure that MUST be complete before any user story implementation can finish.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 Draft Liquibase changesets for merchant table renames, merchant foreign-key renames, merchant code/branch code additions, offering catalogue tables, and order type backfill in `/Users/mehdi/MyProject/blitz-pay/src/main/resources/db/changelog/20260514-001-merchant-refactor.sql` and register it in `/Users/mehdi/MyProject/blitz-pay/src/main/resources/db/changelog/db.changelog-master.yaml`
- [X] T004 [P] Introduce shared merchant/offering persistence types and repositories for the refactor in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/repository/`
- [X] T005 [P] Introduce operational merchant API models for merchant, branch, contact, address, location, and offering payloads in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantOperationsModels.kt`
- [X] T006 [P] Update cross-module merchant API contracts to operational merchant terminology in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantGateway.kt` and related merchant api model files
- [X] T007 [P] Introduce explicit order type and deferred-payment request/response fields in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/Order.kt`
- [X] T008 [P] Add or update foundational fixture/testdata records for operational merchant, branch, offering, and order-type scenarios in `/Users/mehdi/MyProject/blitz-pay/src/test/resources/testdata/merchant/` and `/Users/mehdi/MyProject/blitz-pay/src/test/resources/testdata/order/`

**Checkpoint**: Foundational schema, models, and cross-module contracts are ready for story work

---

## Phase 3: User Story 1 - Manage operational merchants and branches (Priority: P1) 🎯 MVP

**Goal**: Replace onboarding-flavoured operational merchant behaviour with merchant/branch identity, status, address, location, and contact rules from `CONTEXT.md`.

**Independent Test**: Create a merchant with optional merchant-level profile fields, add a branch with mandatory address/location, and verify stable merchant/branch codes plus independent status behaviour.

### Tests for User Story 1 ⚠️

- [X] T009 [P] [US1] Update merchant persistence model tests for operational merchant identity and status mapping in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantApplicationTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantOnboardingLifecycleTest.kt`
- [X] T010 [P] [US1] Add service tests for merchant creation/update with optional contact/address/location and generated merchant codes in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationServiceTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementServiceTest.kt`
- [X] T011 [P] [US1] Add branch service tests covering generated branch codes, mandatory address/location, optional contact override, and independent branch status in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantBranchServiceTest.kt`
- [X] T012 [P] [US1] Update merchant contract tests for operational merchant payloads and branch payload rules in `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantContractTest.kt`

### Implementation for User Story 1

- [X] T013 [US1] Refactor the operational merchant persistence aggregate from onboarding-flavoured identity to merchant identity in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantApplication.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/BusinessProfile.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/PrimaryContact.kt`, and related merchant model files
- [X] T014 [US1] Implement merchant code generation, simple merchant status rules, and optional merchant profile handling in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationService.kt` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementService.kt`
- [X] T015 [US1] Implement branch code generation, branch status handling, mandatory address/location validation, and optional contact override logic in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantBranch.kt` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantBranchService.kt`
- [X] T016 [US1] Update merchant and branch HTTP handlers to expose operational merchant/branch contracts and remove onboarding-oriented request/response language from `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/web/MerchantOnboardingController.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/web/MerchantBranchController.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantOnboardingModels.kt`
- [X] T017 [US1] Update merchant location/contact response shaping and validation helpers to match operational merchant rules in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantLocationService.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantLocationModels.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantApplicationValidator.kt`
- [X] T018 [US1] Update merchant module API documentation for operational merchant and branch contracts in `/Users/mehdi/MyProject/blitz-pay/api-docs/api-doc.yml`
- [X] T019 [US1] Reconcile merchant module modulith and repository verification with the new operational naming in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/MerchantModularityTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/infra/MerchantApplicationRepositoryTest.kt`

**Checkpoint**: Merchant and branch management works with operational codes, statuses, and mandatory/optional profile rules

---

## Phase 4: User Story 2 - Configure merchant-wide offerings and enforce order rules (Priority: P1)

**Goal**: Add merchant-wide offering governance and enforce pre-order, walk-in ordering, deferred-payment, and status/geolocation gates at runtime.

**Independent Test**: Enable offerings on a merchant, create allowed orders, and verify disallowed offering combinations or walk-in location failures are rejected.

### Tests for User Story 2 ⚠️

- [X] T020 [P] [US2] Add merchant offering service tests for catalogue assignment, merchant-wide enforcement, and deferred-payment dependency rules in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementServiceTest.kt`
- [X] T021 [P] [US2] Add geofence and proximity tests for walk-in ordering location validation in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/GeofenceServiceTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantLocationServiceTest.kt`
- [X] T022 [P] [US2] Add order service tests for explicit order type, offering validation, deferred-payment choice, and merchant/branch inactive gating in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/order/service/OrderServiceTest.kt`
- [X] T023 [P] [US2] Update order contract tests for branch-required order creation and explicit order type fields in `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`
- [X] T024 [P] [US2] Update merchant geofence/proximity contract tests for backend walk-in validation behaviour in `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/GeofenceProximityContractTest.kt`

### Implementation for User Story 2

- [X] T025 [US2] Implement merchant offering catalogue seeding, assignment persistence, and dependency validation in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/repository/`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementService.kt`
- [X] T026 [US2] Expose merchant offering read/write support in merchant HTTP/API models and controllers in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantOperationsModels.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/web/MerchantOnboardingController.kt`, and `/Users/mehdi/MyProject/blitz-pay/api-docs/api-doc.yml`
- [X] T027 [US2] Update the merchant gateway to return branch-owned products plus merchant offering and status information required by order validation in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantGateway.kt` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantProductService.kt`
- [X] T028 [US2] Implement backend walk-in eligibility checks using merchant/branch status, branch geofence, and customer geolocation quality rules in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/GeofenceService.kt` and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/ProximityService.kt`
- [X] T029 [US2] Refactor order persistence and request models to require branch ownership, explicit order type, and optional deferred-payment selection in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/persistence/model/Order.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderModels.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/resources/db/changelog/20260514-001-merchant-refactor.sql`
- [X] T030 [US2] Enforce offering, branch, merchant, and geolocation validation in shopper and merchant order flows in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/service/OrderService.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/web/ShopperOrderController.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/web/MerchantOrderController.kt`
- [X] T031 [US2] Update order module documentation and modulith verification for explicit order type and branch-required semantics in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/order/OrderModularityTest.kt` and `/Users/mehdi/MyProject/blitz-pay/api-docs/api-doc.yml`

**Checkpoint**: Merchant-wide offerings govern order flows, and walk-in ordering is backend-validated using geolocation and status gates

---

## Phase 5: User Story 3 - Preserve operational continuity for existing merchant and order data (Priority: P2)

**Goal**: Migrate existing merchant, branch, and order records safely into the operational model without losing branch-linked behavior.

**Independent Test**: Apply migrations to existing records and verify merchants, branches, and orders are retrievable with operational codes, status mapping, and explicit order types.

### Tests for User Story 3 ⚠️

- [X] T032 [P] [US3] Add migration-oriented repository and fixture tests for merchant code, branch code, and status backfill in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/merchant/infra/MerchantApplicationRepositoryTest.kt`
- [X] T033 [P] [US3] Add order migration tests for explicit order type backfill and branch-required reads in `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/order/service/OrderStatusProjectionTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/com/elegant/software/blitzpay/order/service/OrderServiceTest.kt`
- [X] T034 [P] [US3] Update contract fixtures and contract tests to assert migrated response shapes for merchant and order reads in `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantContractTest.kt` and `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/com/elegant/software/blitzpay/order/OrderContractTest.kt`

### Implementation for User Story 3

- [X] T035 [US3] Implement Liquibase data migration and rollback logic for merchant table renames, status mapping, code generation, offering catalogue seeding, and order type backfill in `/Users/mehdi/MyProject/blitz-pay/src/main/resources/db/changelog/20260514-001-merchant-refactor.sql`
- [X] T036 [US3] Update merchant repositories, redaction, audit, and IAM sync components to consume operational merchant identity and renamed foreign keys in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/repository/MerchantApplicationRepository.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRedactionService.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantAuditTrail.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/`
- [X] T037 [US3] Update order repositories and projections to use renamed merchant foreign keys and explicit order type reads in `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/persistence/repository/OrderRepository.kt`, `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/service/OrderStatusProjection.kt`, and `/Users/mehdi/MyProject/blitz-pay/src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderGateway.kt`
- [X] T038 [US3] Update generated API documentation and migration notes for operational merchant continuity in `/Users/mehdi/MyProject/blitz-pay/api-docs/api-doc.yml` and `/Users/mehdi/MyProject/blitz-pay/README.md`

**Checkpoint**: Existing merchant, branch, and order data survive the refactor with operational naming and branch-linked semantics intact

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Finish cross-story consistency, documentation, and full verification.

- [X] T039 [P] Update `CONTEXT.md` and related spec documentation to match any final implementation naming adjustments in `/Users/mehdi/MyProject/blitz-pay/CONTEXT.md` and `/Users/mehdi/MyProject/blitz-pay/specs/017-merchant-refactor/`
- [X] T040 [P] Update repository guidance docs for merchant/order contract changes in `/Users/mehdi/MyProject/blitz-pay/CONSTITUTION.md` and `/Users/mehdi/MyProject/blitz-pay/reference/`
- [X] T041 Run full verification for the refactor with `./gradlew check` and fix any remaining regressions across `/Users/mehdi/MyProject/blitz-pay/src/test/kotlin/` and `/Users/mehdi/MyProject/blitz-pay/src/contractTest/kotlin/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion
- **User Story 2 (Phase 4)**: Depends on Foundational completion and benefits from User Story 1 operational merchant/branch model
- **User Story 3 (Phase 5)**: Depends on Foundational completion and should land after core merchant/order model changes are in place
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - establishes the MVP operational merchant and branch model
- **User Story 2 (P1)**: Can start after Foundational but should integrate on top of User Story 1’s merchant/branch identity changes
- **User Story 3 (P2)**: Depends on the final merchant/order shapes from US1 and US2 so migration targets are stable

### Within Each User Story

- Tests should be updated before or alongside implementation and must pass before the story is complete
- Persistence/model changes before service logic
- Service logic before controller/API wiring
- Contract and documentation updates before story closeout

### Parallel Opportunities

- T004, T005, T006, T007, and T008 can run in parallel after T003
- T009, T010, T011, and T012 can run in parallel inside US1
- T020, T021, T022, T023, and T024 can run in parallel inside US2
- T032, T033, and T034 can run in parallel inside US3
- T039 and T040 can run in parallel during polish

---

## Parallel Example: User Story 1

```bash
# Launch US1 test updates together:
Task: "Update merchant persistence model tests in src/test/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantApplicationTest.kt and MerchantOnboardingLifecycleTest.kt"
Task: "Add service tests in src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationServiceTest.kt and MerchantManagementServiceTest.kt"
Task: "Add branch service tests in src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantBranchServiceTest.kt"
Task: "Update merchant contract tests in src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantContractTest.kt"

# Launch independent US1 implementation slices together:
Task: "Implement merchant code/status/profile handling in src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationService.kt and MerchantManagementService.kt"
Task: "Implement branch code/status/address/location rules in src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantBranch.kt and MerchantBranchService.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate operational merchant and branch management independently before moving on

### Incremental Delivery

1. Land foundational schema/model/API preparation
2. Deliver User Story 1 to establish the operational merchant model
3. Add User Story 2 to enforce offerings and order rules
4. Add User Story 3 to complete migration continuity and rollout safety
5. Finish with full verification and documentation cleanup

### Suggested MVP Scope

- **MVP**: User Story 1 only
- This gives the repo an operational merchant/branch model with correct business identity and status language before enforcing offering and migration complexity

## Notes

- All tasks follow the required checklist format with IDs, story labels where applicable, and file paths
- [P] tasks are limited to work that can proceed without touching the same incomplete file set
- Contract-test, unit-test, and modulith verification coverage are included because repository policy requires them for behavior changes
- Appointment workflow remains out of scope even though `APPOINTMENT_BOOKING` stays in the merchant offering catalogue
