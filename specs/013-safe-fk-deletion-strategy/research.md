# Research: Safe FK Deletion Strategy

**Feature**: 013-safe-fk-deletion-strategy
**Date**: 2026-05-05

---

## Decision 1: Default FK Deletion Behaviour

**Decision**: Prohibit `ON DELETE CASCADE` on all application FK constraints in the `blitzpay` schema. Mandate `RESTRICT` (or the PostgreSQL-equivalent `NO ACTION`) as the unconditional default.

**Rationale**:

`ON DELETE CASCADE` is a database-engine instruction that physically deletes child rows when the parent row is removed. In a Spring Modulith application this is unsafe for three compounding reasons:

1. **Bypasses JPA lifecycle hooks** — `@PreRemove`, `@PostRemove`, and `@EntityListeners` never fire for rows deleted by the database engine directly. Any JPA-level bookkeeping (e.g., clearing a cached ID, updating an in-memory counter) is silently skipped.
2. **Bypasses Spring application events** — Domain events published via `ApplicationEventPublisher` are triggered by explicit `repository.delete(entity)` calls. When the DB deletes rows, no Spring event fires. In Spring Modulith, this means cross-module contracts are violated: a module's data can be removed without the module ever knowing, producing stale read-side views or orphaned references in other modules' tables.
3. **Silent blast radius** — A single `DELETE FROM merchant_applications WHERE id = ?` triggers a database-side cascade that can silently wipe `merchant_branches`, and in turn any module data that references those branches. The application stack sees only one statement; the deletions of downstream rows are invisible to logs, metrics, and auditors.

**Alternatives considered**:

| Alternative | Why rejected |
|-------------|-------------|
| Keep `ON DELETE CASCADE` for "internal" tables | No clear definition of "internal" that survives refactoring; the blast-radius risk is the same regardless |
| `ON DELETE SET NULL` as default | Only valid when the FK column is nullable and an orphaned row makes business sense; unsuitable as a universal default |
| Soft delete everywhere | Addresses audit concerns but does not eliminate the DB-cascade risk on physical records; also out of scope for this spec |
| Rely on JPA `orphanRemoval` instead | `orphanRemoval` is ORM-layer only and still fires application events, but requires loading the collection first — an N+1 risk. Better than DB cascade but explicit `deleteAll…` calls are cleaner. |

---

## Decision 2: Allowed Exceptions

**Decision**: `ON DELETE CASCADE` is permitted **only** for operational log / delivery-attempt tables where:
- The child row has no business identity without the parent (it is not a domain aggregate)
- The child table is write-only from an audit/observability perspective
- The changeset includes an explicit justification comment

**Example acceptable use**: `push_delivery_attempt` rows are write-only observability records with no cross-module consumers. Cascading their deletion when the parent push event is removed is safe and acceptable.

**Rationale**: A blanket prohibition would require audit-log tables to be cleaned up manually, adding operational complexity with no tangible safety benefit for rows that have no lifecycle meaning.

---

## Decision 3: Preferred Pattern for Parent Deletion

**Decision**: Explicit application-level deletion in the owning module's service, within a single `@Transactional` method. Children are deleted first (deepest nesting first), then the parent.

```kotlin
@Transactional
fun deleteOrder(orderId: UUID) {
    paymentAttemptRepository.deleteAllByOrderIdFk(orderId)
    orderItemRepository.deleteAllByOrderIdFk(orderId)
    orderRepository.deleteById(orderId)
}
```

**Why this pattern**:
- Every deletion is visible to JPA → `@PreRemove` / `@PostRemove` fire, Spring events publish
- The transaction wraps all deletes atomically — partial failure rolls back cleanly
- Readable, auditable, testable — no hidden DB-side behaviour

---

## Decision 4: JPA `CascadeType.ALL` / `CascadeType.REMOVE`

**Decision**: Remove `CascadeType.REMOVE` (and `CascadeType.ALL` which includes REMOVE) from `@OneToOne` / `@OneToMany` associations where the child has independent lifecycle significance or where Spring events should fire on deletion. Replace with `CascadeType.PERSIST, CascadeType.MERGE` only. Handle `orphanRemoval` explicitly in the service layer.

**Current violation**: `MerchantApplication.monitoringRecord` uses `cascade = [CascadeType.ALL], orphanRemoval = true`. This means calling `merchantApplicationRepository.delete(app)` silently deletes the `MonitoringRecord` via JPA without a dedicated service event.

**Remediation**: Remove `CascadeType.REMOVE` from the annotation; add an explicit `monitoringRecordRepository.deleteById(app.monitoringRecord!!.id)` call in the service before deleting the application.

**Alternatives considered**:
- Keep `CascadeType.ALL` — rejected; `CascadeType.REMOVE` specifically is the risk vector
- Keep `orphanRemoval = true` — fine for `@ElementCollection` (table-per-element with no independent identity); remove from `@OneToOne` / `@OneToMany` associations that are genuine aggregates

---

## Decision 5: `ON DELETE SET NULL` — Acceptable Pattern

**Decision**: `ON DELETE SET NULL` is acceptable on nullable FK columns where an orphaned child row has clear business meaning (the reference becomes "unknown/unassigned" rather than invalid).

**Example**: If a `merchant_branch` were deleted independently of its application, any `order_orders.merchant_branch_id` (nullable) that referenced it could safely be set to NULL, preserving the order record.

**Current state**: `order_orders.merchant_branch_id` is already nullable but has no FK constraint at all. No action needed.

---

## Existing Violations Inventory

| Table | Column | Constraint Name | Current Behaviour | Action |
|-------|--------|----------------|-------------------|--------|
| `merchant_branches` | `merchant_application_id` | `fk_merchant_branches_application` | CASCADE (via `20260424-002`) | New changeset: drop + recreate as RESTRICT |
| `order_items` | `order_id_fk` | auto-named by PostgreSQL | CASCADE (inline in `20260430-001`) | New changeset: find constraint name, drop + recreate as RESTRICT; add `deleteAllByOrderIdFk` to repo |
| `order_payment_attempts` | `order_id_fk` | auto-named by PostgreSQL | CASCADE (inline in `20260430-001`) | New changeset: find constraint name, drop + recreate as RESTRICT; add `deleteAllByOrderIdFk` to repo |
| `MerchantApplication.monitoringRecord` | N/A (JPA) | N/A | `CascadeType.ALL` + `orphanRemoval` | Remove REMOVE cascade; add explicit deletion in service |

---

## References

- PostgreSQL FK reference actions: `RESTRICT`, `NO ACTION`, `CASCADE`, `SET NULL`, `SET DEFAULT` — RESTRICT and NO ACTION both prevent deletion if children exist; RESTRICT is checked immediately, NO ACTION is deferred to end of transaction (PostgreSQL default for unnamed FK). For our purposes they are equivalent.
- Spring Modulith lifecycle event semantics: `@ApplicationModuleListener` fires after the transaction commits, ensuring events only propagate for committed data.
- JPA `CascadeType`: PERSIST, MERGE, REMOVE, REFRESH, DETACH. REMOVE is the dangerous one; PERSIST and MERGE are safe for parent–child save propagation.