# Tasks: Safe FK Deletion Strategy

**Input**: Design documents from `/specs/013-safe-fk-deletion-strategy/`
**Prerequisites**: plan.md ✓ spec.md ✓ research.md ✓ data-model.md ✓

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

---

## Phase 1: Setup (Prerequisite Discovery)

**Purpose**: Discover auto-generated FK constraint names that must be referenced in remediation changesets. This phase has no code changes — it is an information-gathering step that unblocks Phase 4 (US2).

- [x] T001 Query local dev database to discover auto-generated FK constraint names on `blitzpay.order_items` and `blitzpay.order_payment_attempts` using the SQL in `specs/013-safe-fk-deletion-strategy/data-model.md`, then record the actual names (they will look like `order_items_order_id_fk_fkey` and `order_payment_attempts_order_id_fk_fkey`) — update the placeholder names in the Phase 4 changeset tasks below before writing the SQL files

**Checkpoint**: Actual FK constraint names are known — Phase 4 changesets can now be written correctly

---

## Phase 2: Foundational (Blocking Prerequisite)

**Purpose**: No shared infrastructure is required for this feature. This phase is intentionally empty — user story phases can begin immediately after Phase 1.

**⚠️ NOTE**: Phase 3 (US1) and Phase 4 (US2) are independent of each other and can be worked in parallel once Phase 1 is complete.

---

## Phase 3: User Story 1 — FK Deletion Convention Document (Priority: P1) 🎯 MVP

**Goal**: Add a clearly written convention section to the Liquibase reference guide that prohibits `ON DELETE CASCADE`, explains why, and prescribes three approved alternatives. A developer reading the guide can determine the correct FK deletion approach within 5 minutes.

**Independent Test**: Open `reference/liquibase-best-practices.md` — a Section 10 titled "Foreign Key Deletion Strategy" must exist with: (a) a prohibition statement for ON DELETE CASCADE, (b) the WHY explanation, (c) Pattern A (explicit service deletion), (d) Pattern B (soft delete), (e) Pattern C (SET NULL), (f) the narrow exception for audit tables, (g) prohibition on JPA CascadeType.REMOVE, (h) the verification SQL query.

### Implementation for User Story 1

- [x] T002 [US1] Add Section 10 "Foreign Key Deletion Strategy" to `reference/liquibase-best-practices.md` after the existing Section 9 (Tests) and before the References section, using the full section content from `specs/013-safe-fk-deletion-strategy/plan.md` Phase 1 — include the prohibition rule, WHY explanation, all three approved patterns (A/B/C), the audit-table exception with required justification comment, the JPA CascadeType.REMOVE prohibition, and the verification SQL query
- [x] T003 [US1] Add a new row for "FK deletion strategy" to the Coding Convention References table in `CONSTITUTION.md` pointing to `reference/liquibase-best-practices.md` (Section 10)

**Checkpoint**: Convention document is complete — reviewers can enforce this on any future PR

---

## Phase 4: User Story 2 — Remediate Existing ON DELETE CASCADE Constraints (Priority: P2)

**Goal**: Remove all `ON DELETE CASCADE` FK constraints from the `blitzpay` schema via new forward-only Liquibase changesets; update the owning module services to delete children explicitly before the parent.

**Independent Test**: Apply the two new changesets against a fresh database copy. Then run: `SELECT conname, conrelid::regclass, confdeltype FROM pg_constraint WHERE contype = 'f' AND conrelid::regclass::text LIKE 'blitzpay.%' AND confdeltype = 'a'` — the result must be zero rows. Then call the order deletion and merchant application deletion service paths in unit tests and verify children are deleted before the parent.

### Implementation for User Story 2 — Migrations

- [x] T004 [US2] Create `src/main/resources/db/changelog/20260505-001-restrict-merchant-branches-fk.sql` — changeset `mehdi:20260505-001-restrict-merchant-branches-fk` that drops `fk_merchant_branches_application` and recreates it with `ON DELETE RESTRICT`; include a `-- rollback` directive that reinstates CASCADE; add a comment explaining the remediation
- [x] T005 [US2] Create `src/main/resources/db/changelog/20260505-002-restrict-order-fks.sql` — two changesets (`mehdi:20260505-002-restrict-order-items-fk` and `mehdi:20260505-003-restrict-order-payment-attempts-fk`) that drop the auto-named CASCADE constraints on `order_items.order_id_fk` and `order_payment_attempts.order_id_fk` respectively (use the names discovered in T001) and recreate them as `fk_order_items_order_orders` and `fk_order_payment_attempts_order_orders` with `ON DELETE RESTRICT`; include `-- rollback` directives for both changesets
- [x] T006 [US2] Append two new `- include:` entries to `src/main/resources/db/changelog/db.changelog-master.yaml` after the last existing include: `db/changelog/20260505-001-restrict-merchant-branches-fk.sql` and `db/changelog/20260505-002-restrict-order-fks.sql`

### Implementation for User Story 2 — Repositories

- [x] T007 [P] [US2] Add `fun deleteAllByOrderIdFk(orderIdFk: UUID)` to `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/repository/OrderItemRepository.kt`
- [x] T008 [P] [US2] Add `fun deleteAllByOrderIdFk(orderIdFk: UUID)` to `src/main/kotlin/com/elegant/software/blitzpay/order/persistence/repository/PaymentAttemptRepository.kt`
- [x] T009 [P] [US2] Add `fun deleteAllByMerchantApplicationId(merchantApplicationId: UUID)` to `src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/repository/MerchantBranchRepository.kt`

### Implementation for User Story 2 — Services

- [x] T010 [US2] Add or update `deleteOrder(orderId: UUID)` in `src/main/kotlin/com/elegant/software/blitzpay/order/service/OrderService.kt` — method must be `@Transactional`, call `paymentAttemptRepository.deleteAllByOrderIdFk(orderId)` first, then `orderItemRepository.deleteAllByOrderIdFk(orderId)`, then `orderRepository.deleteById(orderId)`; if deletion is not an exposed operation yet, add it and expose through `OrderGateway` in `src/main/kotlin/com/elegant/software/blitzpay/order/api/OrderGateway.kt`
- [x] T011 [US2] Add or update `deleteMerchantApplication(applicationId: UUID)` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementService.kt` (or `MerchantRedactionService.kt` if that is the correct deletion owner) — method must be `@Transactional`, call `merchantBranchRepository.deleteAllByMerchantApplicationId(applicationId)` first, then delete the monitoring record if present via `monitoringRecordRepository.deleteById(app.monitoringRecord!!.id)`, then `merchantApplicationRepository.delete(app)`

### Tests for User Story 2

- [x] T012 [P] [US2] Write unit test `OrderServiceTest` in `src/test/kotlin/com/elegant/software/blitzpay/order/service/OrderServiceTest.kt` — use Mockito `inOrder` to verify `paymentAttemptRepository.deleteAllByOrderIdFk` is called before `orderItemRepository.deleteAllByOrderIdFk`, which is called before `orderRepository.deleteById`
- [x] T013 [P] [US2] Write unit test `MerchantApplicationDeletionTest` (or add to existing `MerchantManagementServiceTest`) in `src/test/kotlin/com/elegant/software/blitzpay/merchant/service/` — verify `merchantBranchRepository.deleteAllByMerchantApplicationId` is called before `merchantApplicationRepository.delete`; also verify `monitoringRecordRepository.deleteById` is called when `monitoringRecord` is present on the application

**Checkpoint**: Zero CASCADE FKs in blitzpay schema; both service deletion sequences confirmed by unit tests

---

## Phase 5: User Story 3 — Remove JPA CascadeType.REMOVE (Priority: P3)

**Goal**: Eliminate `CascadeType.REMOVE` and `orphanRemoval = true` from `MerchantApplication.monitoringRecord` so that JPA no longer silently deletes `MonitoringRecord` rows. The service layer (already updated in T011) handles orphan cleanup explicitly.

**Independent Test**: Remove `CascadeType.ALL` from the `@OneToOne` annotation and run `./gradlew check`. All tests must pass. Then inspect `MerchantApplication.kt:89` and confirm no `CascadeType.REMOVE` or `CascadeType.ALL` remains.

### Implementation for User Story 3

- [x] T014 [US3] In `src/main/kotlin/com/elegant/software/blitzpay/merchant/persistence/model/MerchantApplication.kt` line 89, change `cascade = [CascadeType.ALL], orphanRemoval = true` to `cascade = [CascadeType.PERSIST, CascadeType.MERGE]` — remove the `orphanRemoval = true` attribute entirely; remove the `CascadeType` import if it is now unused (keep the import if `CascadeType.PERSIST` / `CascadeType.MERGE` still require it — they do, so keep it)
- [x] T015 [US3] Verify that T011 (`deleteMerchantApplication`) already handles the monitoring record explicitly; if the service existed before and already called `merchantApplicationRepository.delete(app)` without deleting the monitoring record first, confirm T011's change covers this gap — no additional code change needed here, just verification

### Tests for User Story 3

- [x] T016 [US3] Run `./gradlew check` and confirm all unit and contract tests pass with the `CascadeType.ALL` removed — if any test was implicitly relying on JPA cascade-remove behaviour (e.g., deleting an application and expecting the monitoring record to vanish automatically), update those tests to call the service's explicit deletion path instead

**Checkpoint**: No `CascadeType.REMOVE` or `CascadeType.ALL` in any entity; `./gradlew check` is green

---

## Phase 6: Polish & Verification

**Purpose**: Final validation across all stories

- [x] T017 Run `./gradlew check` from repo root — confirm all unit tests and contract tests pass
- [ ] T018 [P] Run the FK convention verification SQL from `specs/013-safe-fk-deletion-strategy/data-model.md` against the local dev database — result must be zero rows (no CASCADE FKs remaining in `blitzpay` schema)
- [x] T019 [P] Grep the entire codebase for `ON DELETE CASCADE` in `.sql` files to confirm no new violations were introduced: `grep -rn "ON DELETE CASCADE" src/main/resources/db/`; the only remaining hit (if any) should be in changeset rollback directives
- [x] T020 [P] Grep Kotlin source for `CascadeType.ALL` and `CascadeType.REMOVE` to confirm no other entities have violations: `grep -rn "CascadeType.ALL\|CascadeType.REMOVE" src/main/kotlin/`; result must be empty or each hit must have a documented justification

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Empty — no blocking work
- **Phase 3 (US1)**: Can start after Phase 1; independent of Phase 4
- **Phase 4 (US2)**: Requires Phase 1 (T001 — FK constraint names); T007/T008/T009 and T004/T005/T006 can run in parallel once T001 is done; T010/T011 depend on T007/T008/T009; T012/T013 depend on T010/T011
- **Phase 5 (US3)**: Requires T011 to be complete before T014 is committed (service must handle orphan before entity cascade is removed)
- **Phase 6 (Polish)**: Requires all story phases complete

### User Story Dependencies

- **US1 (P1)**: Independent — can start immediately after Phase 1
- **US2 (P2)**: Requires Phase 1 (T001) — no dependency on US1
- **US3 (P3)**: Requires US2 T011 to be complete before T014

### Parallel Opportunities

- US1 (T002–T003) and US2 migrations (T004–T006) can run in parallel
- US2 repository tasks (T007, T008, T009) are all parallel
- US2 unit tests (T012, T013) are parallel once T010/T011 are done
- Phase 6 verification tasks (T018, T019, T020) are all parallel

---

## Parallel Example: User Story 2 Repositories

```
# All three repository additions can run simultaneously (different files):
Task T007: "Add deleteAllByOrderIdFk to OrderItemRepository.kt"
Task T008: "Add deleteAllByOrderIdFk to PaymentAttemptRepository.kt"
Task T009: "Add deleteAllByMerchantApplicationId to MerchantBranchRepository.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (T001 — discover constraint names)
2. Complete Phase 3 (T002–T003 — reference doc + CONSTITUTION.md)
3. **STOP and VALIDATE**: Reference guide is complete and reviewed
4. The convention is now established; US2/US3 remediate technical debt at the team's pace

### Incremental Delivery

1. Phase 1 → Convention doc (US1) → Convention established and enforced in review
2. Add migrations + repository + service (US2) → CASCADE FKs eliminated from schema
3. Add entity annotation change (US3) → JPA cascade-remove eliminated
4. Each phase can be delivered as a separate commit or PR

### Single Developer Sequential Order

T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013 → T014 → T015 → T016 → T017 → T018 → T019 → T020

---

## Notes

- T001 is a discovery task — run the SQL, record the names, then proceed. No code file is modified.
- Rollback directives in T004/T005 must reinstate the CASCADE constraints so that `liquibase rollback` can recover staging environments if needed.
- T015 is a verification step, not a code change; it can be marked done after T011 is confirmed correct.
- Do not combine T014 and T010/T011 in the same commit — if the cascade is removed before the service handles orphans, any intermediate state that calls `merchantApplicationRepository.delete(app)` will leave orphaned `MonitoringRecord` rows.