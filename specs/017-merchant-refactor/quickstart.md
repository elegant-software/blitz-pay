# Quickstart: Merchant Refactor

## Goal

Validate the operational merchant model, merchant-wide offerings, branch requirements, and explicit order-type enforcement after implementation.

## 1. Verify merchant creation

1. Create a merchant with:
   - merchant name
   - simple merchant status
   - no branches
   - no offerings
   - no merchant address, location, or contact info
2. Confirm the merchant is accepted.
3. Confirm the response includes a system-generated merchant code and does not expose onboarding workflow fields.

## 2. Verify branch requirements

1. Create a branch under the merchant.
2. Omit branch address or branch location and confirm the request is rejected.
3. Submit the same branch with full structured address and geospatial location.
4. Confirm the response includes a system-generated branch code and independent branch status.

## 3. Verify offering management

1. Enable `PRE_ORDER`.
2. Enable `WALK_IN_ORDERING`.
3. Attempt to enable `DEFERRED_PAYMENT` without either ordering offering on a different merchant and confirm rejection.
4. Enable `DEFERRED_PAYMENT` after `PRE_ORDER` or `WALK_IN_ORDERING` and confirm acceptance.

## 4. Verify branch gating

1. Set merchant status to inactive and confirm new branch-facing customer flows are rejected.
2. Reactivate merchant, set branch status to inactive, and confirm new customer flows are rejected.
3. Confirm existing orders remain readable and are not retroactively invalidated.

## 5. Verify order-type validation

1. Create a `PRE_ORDER` against the branch while the customer is away from the branch.
2. Create a `PRE_ORDER` while the customer is at the branch and confirm it is still accepted.
3. Create a `WALK_IN_ORDERING` request without customer geolocation and confirm rejection.
4. Create a `WALK_IN_ORDERING` request with stale or low-accuracy geolocation and confirm rejection.
5. Create a `WALK_IN_ORDERING` request with valid branch-local geolocation and confirm acceptance.

## 6. Verify migration outcomes

1. Load an existing merchant record created before the refactor.
2. Confirm it now exposes merchant code, merchant status, and operational merchant language.
3. Load existing branch records and confirm each has a branch code.
4. Load existing orders and confirm each has a branch reference and explicit order type.
