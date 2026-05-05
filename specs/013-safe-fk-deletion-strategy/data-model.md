# Data Model: Safe FK Deletion Strategy

**Feature**: 013-safe-fk-deletion-strategy
**Date**: 2026-05-05

No new tables or columns are introduced. This feature changes FK constraint deletion behaviour on existing tables and removes a JPA cascade annotation.

---

## Constraint Changes

### 1. `merchant_branches.fk_merchant_branches_application`

| Attribute | Before | After |
|-----------|--------|-------|
| Constraint name | `fk_merchant_branches_application` | `fk_merchant_branches_application` (same) |
| References | `merchant_applications(id)` | `merchant_applications(id)` (same) |
| On delete | `CASCADE` | `RESTRICT` |
| Changeset | `20260424-002-branch-merchant-fk-cascade.sql` (applied, immutable) | New: `20260505-001-restrict-merchant-branches-fk.sql` |

### 2. `order_items` → `order_orders`

| Attribute | Before | After |
|-----------|--------|-------|
| Column | `order_id_fk` | `order_id_fk` (same) |
| On delete | `CASCADE` (auto-named constraint) | `RESTRICT` (renamed to `fk_order_items_order_orders`) |
| Changeset | `20260430-001-create-order-tables.sql` (applied, immutable) | New: `20260505-002-restrict-order-fks.sql` |

### 3. `order_payment_attempts` → `order_orders`

| Attribute | Before | After |
|-----------|--------|-------|
| Column | `order_id_fk` | `order_id_fk` (same) |
| On delete | `CASCADE` (auto-named constraint) | `RESTRICT` (renamed to `fk_order_payment_attempts_order_orders`) |
| Changeset | `20260430-001-create-order-tables.sql` (applied, immutable) | New: `20260505-002-restrict-order-fks.sql` |

---

## JPA Entity Changes

### `MerchantApplication.monitoringRecord`

| Attribute | Before | After |
|-----------|--------|-------|
| File | `merchant/persistence/model/MerchantApplication.kt:89` | Same |
| Annotation | `@OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)` | `@OneToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE])` |
| Deletion responsibility | JPA (implicit, no event) | `MerchantManagementService` (explicit, auditable) |

---

## Repository Changes

### `OrderItemRepository`

Add deletion method:
```kotlin
fun deleteAllByOrderIdFk(orderIdFk: UUID)
```

### `PaymentAttemptRepository`

Add deletion method:
```kotlin
fun deleteAllByOrderIdFk(orderIdFk: UUID)
```

### `MerchantBranchRepository` (no change needed)

`deleteAllByMerchantApplicationId` is available via `JpaRepository` derivation; verify or add explicitly:
```kotlin
fun deleteAllByMerchantApplicationId(merchantApplicationId: UUID)
```

### `MonitoringRecordRepository` (no change needed)

`deleteById` is already provided by `JpaRepository<MonitoringRecord, UUID>`.

---

## Service Changes

### `OrderService`

Any `deleteOrder(orderId)` operation must follow this sequence:
1. `paymentAttemptRepository.deleteAllByOrderIdFk(orderId)`
2. `orderItemRepository.deleteAllByOrderIdFk(orderId)`
3. `orderRepository.deleteById(orderId)`

All three steps wrapped in `@Transactional`.

### `MerchantManagementService` (or `MerchantRedactionService`)

Any merchant application deletion must follow:
1. `merchantBranchRepository.deleteAllByMerchantApplicationId(applicationId)`
2. `monitoringRecordRepository.deleteById(app.monitoringRecord!!.id)` (if present)
3. `merchantApplicationRepository.deleteById(applicationId)`

All three steps wrapped in `@Transactional`.

---

## Migration File Plan

```
src/main/resources/db/changelog/
├── 20260505-001-restrict-merchant-branches-fk.sql    ← drop CASCADE, add RESTRICT on fk_merchant_branches_application
└── 20260505-002-restrict-order-fks.sql               ← drop CASCADE on order_items and order_payment_attempts, add RESTRICT with named constraints
```

Both files added to `db.changelog-master.yaml` in chronological order after the existing entries.

---

## Verification Query

After remediation changesets are applied, the following SQL should return zero rows:

```sql
SELECT conname, conrelid::regclass, confdeltype
FROM pg_constraint
WHERE contype = 'f'
  AND conrelid::regclass::text LIKE 'blitzpay.%'
  AND confdeltype = 'a';  -- 'a' = CASCADE
```

`confdeltype` values: `a` = CASCADE, `r` = RESTRICT, `n` = SET NULL, `d` = SET DEFAULT, `\0` = NO ACTION.