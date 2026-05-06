# Data Model: Keycloak Merchant & Branch Group Sync

**Feature**: 014-keycloak-merchant-groups  
**Date**: 2026-05-06

---

## Existing Entities (unchanged)

### `MerchantApplication` (`merchant_applications`)

The authoritative source of merchant identity data.

| Field | Type | Role in this feature |
|-------|------|----------------------|
| `id` | UUID | Keycloak group identifier token (`merchant_<id>`) and `merchant_id` attribute |
| `businessProfile.legalBusinessName` | String | Keycloak `merchant_name` attribute |
| `applicationReference` | String | Informational; not used in Keycloak group name |
| `status` | Enum | Sync is triggered on `ACTIVE` transition (direct registration) |

### `MerchantBranch` (`merchant_branches`)

The authoritative source of branch identity data.

| Field | Type | Role in this feature |
|-------|------|----------------------|
| `id` | UUID | Keycloak group identifier token (`branch_<id>`) and `branch_id` attribute |
| `merchantApplicationId` | UUID | Links branch to parent merchant group |
| `name` | String | Keycloak `branch_name` attribute |

---

## New Domain Events (published by `merchant` module)

These event types live in `merchant.api` (a `@NamedInterface` package) so they can be consumed by `merchant.iam` without accessing `merchant.internal`.

### `MerchantActivated`

Published by `MerchantRegistrationService` when a merchant is saved with status `ACTIVE`.

| Field | Type | Description |
|-------|------|-------------|
| `merchantId` | UUID | `MerchantApplication.id` |
| `merchantName` | String | `BusinessProfile.legalBusinessName` |

### `BranchCreated`

Published by `MerchantBranchService` when a new `MerchantBranch` is persisted.

| Field | Type | Description |
|-------|------|-------------|
| `branchId` | UUID | `MerchantBranch.id` |
| `merchantId` | UUID | `MerchantBranch.merchantApplicationId` |
| `branchName` | String | `MerchantBranch.name` |
| `merchantName` | String | Parent merchant's `legalBusinessName` |

### `MerchantNameUpdated`

Published by `MerchantManagementService` (or `MerchantRegistrationService`) when `businessProfile.legalBusinessName` changes.

| Field | Type | Description |
|-------|------|-------------|
| `merchantId` | UUID | `MerchantApplication.id` |
| `newName` | String | Updated legal business name |

### `BranchNameUpdated`

Published by `MerchantBranchService` when `MerchantBranch.name` changes via `updateDetails()`.

| Field | Type | Description |
|-------|------|-------------|
| `branchId` | UUID | `MerchantBranch.id` |
| `merchantId` | UUID | `MerchantBranch.merchantApplicationId` |
| `newName` | String | Updated branch name |

---

## Keycloak Group Structure (external state)

This is not a database table — it lives in Keycloak. Documented here for clarity.

```
/merchants                              ← root group (auto-created if absent)
  /merchant_<merchantId-uuid>           ← one per MerchantApplication
      attributes:
        merchant_id   = <uuid>
        merchant_name = <legalBusinessName>

    /branch_<branchId-uuid>             ← one per MerchantBranch
        attributes:
          branch_id   = <uuid>
          branch_name = <name>
          merchant_id = <merchantId-uuid>
```

---

## No New Database Tables (Phase 1)

The Modulith Event Publication Registry (table `event_publication`, created by `20260419-001-create-event-publication.sql`) provides durable at-least-once delivery for the new domain events. No additional tables are required for the core feature.

**Optional future addition** (out of scope): `merchant_iam_sync_log` for observability — tracks successful Keycloak group creations/updates with timestamps.

---

## State Transitions That Trigger Events

```
MerchantApplication.status
  DRAFT ──registerDirect()──► ACTIVE    ← publishes MerchantActivated
  * ──updateProfile()──► *              ← publishes MerchantNameUpdated (if name changed)

MerchantBranch
  (new)  ──save()──► persisted          ← publishes BranchCreated
  (existing) ──updateDetails()──► *     ← publishes BranchNameUpdated (if name changed)
```

---

## Validation Rules

- `merchantId` on all events must reference an existing `merchant_applications` row (guaranteed by the publishing service's own invariants).
- `merchantName` must not be blank (enforced by `MerchantRegistrationService` existing validation).
- `branchName` must not be blank (enforced by `MerchantBranchService` existing validation).
- Keycloak group names are URL-safe; UUIDs use only `[a-f0-9-]` characters — no special encoding needed.
