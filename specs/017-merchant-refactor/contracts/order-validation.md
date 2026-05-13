# Contract: Order Validation Under Merchant Refactor

## Purpose

Define the request/response expectations for branch-linked order creation after merchant refactoring.

## Shopper Order Create

Shopper order creation must include:

- `branchId`
- `orderType`
- `items`
- payment selection fields already required by the order module
- customer geolocation when requesting `WALK_IN_ORDERING`

Validation rules:

- reject when `branchId` is missing
- reject when `orderType` is missing
- reject when merchant does not have the requested offering enabled
- reject when merchant is inactive
- reject when branch is inactive
- allow `PRE_ORDER` regardless of customer proximity
- reject `WALK_IN_ORDERING` when customer geolocation is absent
- reject `WALK_IN_ORDERING` when customer geolocation is stale or insufficiently accurate
- reject `WALK_IN_ORDERING` when backend branch-proximity validation fails

Response shape additions:

- `branchId` is always present
- `orderType` is always present
- indicate whether deferred payment was chosen for the order when applicable

## Merchant Order Create

Merchant order creation must include:

- `merchantId`
- `branchId`
- `orderType`
- `items`

Validation rules:

- reject when merchant and branch do not align
- reject when requested order type is not enabled for the merchant
- allow merchant-created `PRE_ORDER` and `WALK_IN_ORDERING` according to the same offering rules
- if `WALK_IN_ORDERING` is used in a flow that still depends on end-customer proximity, the backend must validate it against the same location rules before final acceptance

## Deferred Payment Rules

- `DEFERRED_PAYMENT` is merchant-enabled, not merchant-mandatory
- an eligible order may choose whether to use deferred payment
- reject deferred payment selection when merchant has not enabled `DEFERRED_PAYMENT`
- reject merchant offering configuration that enables `DEFERRED_PAYMENT` without `PRE_ORDER` or `WALK_IN_ORDERING`
