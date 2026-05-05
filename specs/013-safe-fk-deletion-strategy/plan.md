# Implementation Plan: Safe FK Deletion Strategy

**Branch**: `013-safe-fk-deletion-strategy` | **Date**: 2026-05-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/013-safe-fk-deletion-strategy/spec.md`

## Summary

Prohibit `ON DELETE CASCADE` on application FK constraints in the `blitzpay` schema and establish explicit application-layer deletion as the project convention. Three existing CASCADE constraints are remediated via new forward-only Liquibase changesets; two repositories gain bulk-delete methods; one service is updated with explicit child-before-parent deletion ordering; one JPA entity drops `CascadeType.REMOVE`; and `reference/liquibase-best-practices.md` gains a new FK deletion strategy section that codifies the convention for all future contributors.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA, Liquibase  
**Storage**: PostgreSQL 16 (`blitzpay` schema), `ddl-auto: none` — all schema changes via Liquibase  
**Testing**: JUnit 5 + Mockito Kotlin (unit); `WebTestClient` contract tests (`contract-test` profile)  
**Target Platform**: Linux server (Docker / Kubernetes)  
**Project Type**: Web service (reactive, Spring WebFlux)  
**Performance Goals**: Standard — no performance impact expected from constraint type change  
**Constraints**: Existing applied changesets are immutable; remediation must use new forward-only changesets. Tests must pass without modification.  
**Scale/Scope**: Three FK constraints, two repositories, one service, one entity annotation, one reference document

## Constitution Check

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin only (no Java source) | PASS | All changes are `.kt` and `.sql` files |
| Spring Modulith module boundaries | PASS | Service-layer deletion respects module ownership; no cross-module repository access introduced |
| Liquibase owns all schema evolution (`ddl-auto: none`) | PASS | All constraint changes via new `.sql` changesets |
| Every changeset has a `-- rollback` directive | PASS | Required for all new changesets (plan enforces this) |
| Every behaviour change must have tests | PASS | Repository deletion methods and service ordering require unit tests |
| Contract tests for any new/changed endpoints | PASS | No HTTP API changes in this feature |
| `open-in-view: false` preserved | PASS | No configuration changes |
| Secrets not committed | PASS | No secrets involved |

## Project Structure

### Documentation (this feature)

```text
specs/013-safe-fk-deletion-strategy/
├── plan.md              ← This file
├── spec.md              ← Feature specification
├── research.md          ← Phase 0 research findings
├── data-model.md        ← Phase 1 design
└── checklists/
    └── requirements.md  ← Quality validation checklist
```

### Source Code Changes

```text
reference/
└── liquibase-best-practices.md          ← Add Section 10: FK Deletion Strategy

src/main/resources/db/changelog/
├── db.changelog-master.yaml             ← Append two new includes
├── 20260505-001-restrict-merchant-branches-fk.sql   ← NEW
└── 20260505-002-restrict-order-fks.sql              ← NEW

src/main/kotlin/com/elegant/software/blitzpay/
├── order/
│   ├── persistence/repository/
│   │   ├── OrderItemRepository.kt       ← Add deleteAllByOrderIdFk
│   │   └── PaymentAttemptRepository.kt  ← Add deleteAllByOrderIdFk
│   └── service/
│       └── OrderService.kt              ← Add/update deleteOrder with explicit child deletion
├── merchant/
│   ├── persistence/
│   │   ├── model/
│   │   │   └── MerchantApplication.kt   ← Remove CascadeType.REMOVE from monitoringRecord
│   │   └── repository/
│   │       └── MerchantBranchRepository.kt ← Add deleteAllByMerchantApplicationId
│   └── service/
│       └── MerchantManagementService.kt ← Add explicit child deletion before application delete

src/test/kotlin/com/elegant/software/blitzpay/
├── order/
│   └── service/
│       └── OrderServiceTest.kt          ← Test: children deleted before parent
└── merchant/
    └── service/
        └── MerchantManagementServiceTest.kt ← Test: branches and monitoringRecord deleted before app
```

## Implementation Phases

---

### Phase 1 — Reference Document

**Task 1.1** — Add FK deletion strategy section to `reference/liquibase-best-practices.md`

Add a new **Section 10** between the current Section 9 (Tests) and the References section:

```markdown
## 10. Foreign Key Deletion Strategy

### Rule: Never use ON DELETE CASCADE on application FK constraints

All foreign key constraints in the `blitzpay` schema MUST use the default `RESTRICT`
(or explicit `ON DELETE RESTRICT`). `ON DELETE CASCADE` is **prohibited** because it:

1. Silently deletes child rows at the database layer, bypassing JPA `@PreRemove` / `@PostRemove` lifecycle hooks
2. Prevents Spring `ApplicationEventPublisher` events from firing for the deleted rows
3. Violates Spring Modulith module boundaries — another module's data can be deleted without that module's knowledge

### Required FK declaration (SQL changeset)

Always declare the FK constraint explicitly with a name:

    CONSTRAINT fk_{child_table}_{parent_table}
        FOREIGN KEY (column)
        REFERENCES blitzpay.parent_table (id)
        ON DELETE RESTRICT

Alternatively, omit `ON DELETE ...` entirely — PostgreSQL defaults to `NO ACTION`,
which is equivalent to `RESTRICT` for non-deferred constraints.

### Approved deletion patterns

**Pattern A — Explicit service-layer child deletion (preferred)**

The owning module's service deletes children first, then the parent, all within a single `@Transactional` method:

    @Transactional
    fun deleteOrder(orderId: UUID) {
        paymentAttemptRepository.deleteAllByOrderIdFk(orderId)
        orderItemRepository.deleteAllByOrderIdFk(orderId)
        orderRepository.deleteById(orderId)
    }

**Pattern B — Soft delete / logical delete (for audit-sensitive data)**

Add a `deleted_at TIMESTAMPTZ NULL` column. Queries filter on `WHERE deleted_at IS NULL`.
No physical `DELETE` is issued; the row is retained for audit. Suitable for financial or
compliance-sensitive entities.

**Pattern C — ON DELETE SET NULL (nullable optional references only)**

Acceptable when the FK column is nullable and an orphaned child record has clear
business meaning (the reference becomes "unknown"):

    merchant_branch_id UUID NULL REFERENCES blitzpay.merchant_branches (id) ON DELETE SET NULL

### Exception: audit / observability tables

`ON DELETE CASCADE` is acceptable for write-only operational log or delivery-attempt
tables where:
- The child row has no business identity without the parent
- No cross-module consumer reads the child table
- The changeset includes an explicit justification comment, e.g.:
  `-- cascade-exception: push_delivery_attempt rows are observability-only with no cross-module consumers`

### Prohibition on JPA CascadeType.REMOVE

Do not use `CascadeType.REMOVE` or `CascadeType.ALL` on `@OneToOne` / `@OneToMany`
associations for entities with independent domain significance. JPA cascade-remove
fires lifecycle hooks but still bypasses Spring application events. Use explicit
service-layer deletion instead.

`CascadeType.PERSIST` and `CascadeType.MERGE` are safe and encouraged for propagating
saves from parent to child.

### Verification

After any schema migration, verify no unintended cascade FKs exist:

    SELECT conname, conrelid::regclass, confdeltype
    FROM pg_constraint
    WHERE contype = 'f'
      AND conrelid::regclass::text LIKE 'blitzpay.%'
      AND confdeltype = 'a';  -- 'a' = CASCADE — should return zero rows
```

---

### Phase 2 — Liquibase Remediation Changesets

**Task 2.1** — Create `20260505-001-restrict-merchant-branches-fk.sql`

```sql
-- liquibase formatted sql

-- changeset mehdi:20260505-001-restrict-merchant-branches-fk
-- Remediate: fk_merchant_branches_application was CASCADE (added in 20260424-002).
-- Recreate as RESTRICT to enforce explicit service-layer deletion.
ALTER TABLE blitzpay.merchant_branches
    DROP CONSTRAINT fk_merchant_branches_application,
    ADD CONSTRAINT fk_merchant_branches_application
        FOREIGN KEY (merchant_application_id)
        REFERENCES blitzpay.merchant_applications (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT fk_merchant_branches_application, ADD CONSTRAINT fk_merchant_branches_application FOREIGN KEY (merchant_application_id) REFERENCES blitzpay.merchant_applications (id) ON DELETE CASCADE;
```

**Task 2.2** — Create `20260505-002-restrict-order-fks.sql`

The inline `REFERENCES ... ON DELETE CASCADE` in `20260430-001` creates auto-named constraints. Discover the auto-assigned names by querying `pg_constraint` against the local dev DB, then drop and recreate with explicit names:

```sql
-- liquibase formatted sql

-- changeset mehdi:20260505-002-restrict-order-items-fk
-- Remediate: order_items.order_id_fk was CASCADE (auto-named in 20260430-001).
ALTER TABLE blitzpay.order_items
    DROP CONSTRAINT order_items_order_id_fk_fkey,   -- auto-assigned name; verify before applying
    ADD CONSTRAINT fk_order_items_order_orders
        FOREIGN KEY (order_id_fk)
        REFERENCES blitzpay.order_orders (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.order_items DROP CONSTRAINT fk_order_items_order_orders, ADD CONSTRAINT order_items_order_id_fk_fkey FOREIGN KEY (order_id_fk) REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE;

-- changeset mehdi:20260505-003-restrict-order-payment-attempts-fk
-- Remediate: order_payment_attempts.order_id_fk was CASCADE (auto-named in 20260430-001).
ALTER TABLE blitzpay.order_payment_attempts
    DROP CONSTRAINT order_payment_attempts_order_id_fk_fkey,   -- auto-assigned name; verify before applying
    ADD CONSTRAINT fk_order_payment_attempts_order_orders
        FOREIGN KEY (order_id_fk)
        REFERENCES blitzpay.order_orders (id)
        ON DELETE RESTRICT;
-- rollback ALTER TABLE blitzpay.order_payment_attempts DROP CONSTRAINT fk_order_payment_attempts_order_orders, ADD CONSTRAINT order_payment_attempts_order_id_fk_fkey FOREIGN KEY (order_id_fk) REFERENCES blitzpay.order_orders (id) ON DELETE CASCADE;
```

> **Before writing** the changeset: run the verification query against the local dev database to get the actual auto-generated constraint names:
> ```sql
> SELECT conname FROM pg_constraint
> WHERE conrelid = 'blitzpay.order_items'::regclass AND contype = 'f';
>
> SELECT conname FROM pg_constraint
> WHERE conrelid = 'blitzpay.order_payment_attempts'::regclass AND contype = 'f';
> ```

**Task 2.3** — Update `db.changelog-master.yaml`

Append after the last existing include:
```yaml
  - include:
      file: db/changelog/20260505-001-restrict-merchant-branches-fk.sql
  - include:
      file: db/changelog/20260505-002-restrict-order-fks.sql
```

---

### Phase 3 — Repository Changes

**Task 3.1** — `OrderItemRepository.kt`: add `deleteAllByOrderIdFk(orderIdFk: UUID)`

**Task 3.2** — `PaymentAttemptRepository.kt`: add `deleteAllByOrderIdFk(orderIdFk: UUID)`

**Task 3.3** — `MerchantBranchRepository.kt`: add `deleteAllByMerchantApplicationId(merchantApplicationId: UUID)`

---

### Phase 4 — Service Layer Updates

**Task 4.1** — `OrderService.kt`

If a `deleteOrder` method exists, update it. If not, add one (and expose it through `OrderGateway` if deletion is a supported operation):

```kotlin
@Transactional
fun deleteOrder(orderId: UUID) {
    paymentAttemptRepository.deleteAllByOrderIdFk(orderId)
    orderItemRepository.deleteAllByOrderIdFk(orderId)
    orderRepository.deleteById(orderId)
}
```

**Task 4.2** — `MerchantManagementService.kt` (or `MerchantRedactionService.kt`)

Add or update merchant application deletion to explicitly remove dependents:

```kotlin
@Transactional
fun deleteMerchantApplication(applicationId: UUID) {
    val app = merchantApplicationRepository.findById(applicationId)
        .orElseThrow { EntityNotFoundException("MerchantApplication $applicationId not found") }
    merchantBranchRepository.deleteAllByMerchantApplicationId(applicationId)
    app.monitoringRecord?.let { monitoringRecordRepository.deleteById(it.id) }
    merchantApplicationRepository.delete(app)
}
```

---

### Phase 5 — JPA Entity Change

**Task 5.1** — `MerchantApplication.kt:89`

Change:
```kotlin
@OneToOne(fetch = FetchType.EAGER, optional = true, cascade = [CascadeType.ALL], orphanRemoval = true)
```
To:
```kotlin
@OneToOne(fetch = FetchType.EAGER, optional = true, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
```

Remove the `orphanRemoval = true`. The service now handles orphan cleanup explicitly.

---

### Phase 6 — Tests

**Task 6.1** — Unit test for `OrderService.deleteOrder`

```
OrderServiceTest: "deleteOrder deletes payment attempts and items before the order"
  → verify paymentAttemptRepository.deleteAllByOrderIdFk called with orderId
  → verify orderItemRepository.deleteAllByOrderIdFk called with orderId
  → verify orderRepository.deleteById called with orderId
  → verify delete order is called AFTER child deletes (argument captor / inOrder)
```

**Task 6.2** — Unit test for `MerchantManagementService.deleteMerchantApplication`

```
MerchantManagementServiceTest: "deleteMerchantApplication deletes branches and monitoring record before the application"
  → verify merchantBranchRepository.deleteAllByMerchantApplicationId called
  → verify monitoringRecordRepository.deleteById called (when monitoringRecord present)
  → verify merchantApplicationRepository.delete called after dependencies
```

---

## Complexity Tracking

No Constitution violations. All changes are within existing module boundaries.

## Delivery Order

1. **Phase 1** (reference doc) — no dependencies, can be done first
2. **Phase 2** (migrations) — prerequisite: look up auto-generated constraint names from local DB before authoring Task 2.2
3. **Phase 3** (repositories) — no dependencies
4. **Phase 4** (services) — depends on Phase 3 repositories
5. **Phase 5** (entity annotation) — depends on Phase 4 (service must be updated first so orphan cleanup exists)
6. **Phase 6** (tests) — depends on Phases 3–5

Run `./gradlew check` after Phase 6 to confirm all tests pass.