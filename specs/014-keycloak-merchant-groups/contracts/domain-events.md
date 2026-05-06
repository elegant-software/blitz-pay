# Contract: Domain Events — Merchant IAM Sync

**Module**: `merchant` (publisher) → `merchant.iam` (subscriber)  
**Transport**: Spring Modulith `ApplicationEventPublisher` / `@ApplicationModuleListener`

---

## Event: `MerchantActivated`

**Package**: `com.elegant.software.blitzpay.merchant.api`  
**Published by**: `MerchantRegistrationService.register()`  
**Consumed by**: `merchant.iam` — triggers creation of `/merchants/merchant_<id>` Keycloak group

```kotlin
data class MerchantActivated(
    val merchantId: UUID,
    val merchantName: String,
)
```

**Invariants**:
- `merchantId` is the persisted `MerchantApplication.id`
- `merchantName` is non-blank
- Published within the same transaction that persists the `MerchantApplication`

---

## Event: `BranchCreated`

**Package**: `com.elegant.software.blitzpay.merchant.api`  
**Published by**: `MerchantBranchService.create()`  
**Consumed by**: `merchant.iam` — triggers creation of `/merchants/merchant_<merchantId>/branch_<branchId>` Keycloak sub-group

```kotlin
data class BranchCreated(
    val branchId: UUID,
    val merchantId: UUID,
    val branchName: String,
    val merchantName: String,
)
```

**Invariants**:
- `branchId` is the persisted `MerchantBranch.id`
- `merchantId` matches an existing `MerchantApplication`
- Both name fields are non-blank
- Published within the same transaction that persists the `MerchantBranch`

---

## Event: `MerchantNameUpdated`

**Package**: `com.elegant.software.blitzpay.merchant.api`  
**Published by**: `MerchantManagementService` or `MerchantRegistrationService` on name change  
**Consumed by**: `merchant.iam` — updates `merchant_name` attribute on the existing Keycloak group

```kotlin
data class MerchantNameUpdated(
    val merchantId: UUID,
    val newName: String,
)
```

**Invariants**:
- Only published when `legalBusinessName` actually changes (not on every `updateProfile()` call)
- `newName` is non-blank

---

## Event: `BranchNameUpdated`

**Package**: `com.elegant.software.blitzpay.merchant.api`  
**Published by**: `MerchantBranchService.update()` on name change  
**Consumed by**: `merchant.iam` — updates `branch_name` attribute on the existing Keycloak sub-group

```kotlin
data class BranchNameUpdated(
    val branchId: UUID,
    val merchantId: UUID,
    val newName: String,
)
```

**Invariants**:
- Only published when `name` actually changes
- `newName` is non-blank

---

## Delivery Guarantee

All four events use Spring Modulith's Event Publication Registry (`event_publication` table) via `@ApplicationModuleListener`. This guarantees at-least-once delivery and automatic retry on application restart if the listener fails.

The `merchant.iam` listener must therefore be idempotent for all four event types.
