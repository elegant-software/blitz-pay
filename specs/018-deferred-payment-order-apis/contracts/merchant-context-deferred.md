# Contract: Merchant Context — deferredPaymentAvailable (US1)

## Endpoint

`POST /v1/proximity`

## Change

`ProximityResponse.merchant.deferredPaymentAvailable` is a new Boolean field on `MerchantContext`.

## Response shape (enter event, merchant with DEFERRED_PAYMENT offering)

```json
{
  "recorded": true,
  "action": "ENTER",
  "merchant": {
    "merchantId": "11111111-1111-1111-1111-111111111111",
    "name": "Acme Café",
    "logoUrl": null,
    "deferredPaymentAvailable": true,
    "activePaymentChannels": ["TRUELAYER"],
    "branches": [
      {
        "branchId": "33333333-3333-3333-3333-333333333333",
        "name": "Acme Café Berlin"
      }
    ]
  }
}
```

## Response shape (merchant WITHOUT DEFERRED_PAYMENT offering)

```json
{
  "recorded": true,
  "action": "ENTER",
  "merchant": {
    "merchantId": "22222222-2222-2222-2222-222222222222",
    "name": "Quick Eats",
    "deferredPaymentAvailable": false,
    "activePaymentChannels": ["TRUELAYER"],
    "branches": []
  }
}
```

## Contract test coverage required

- `deferredPaymentAvailable=true` when merchant has DEFERRED_PAYMENT offering
- `deferredPaymentAvailable=false` (or absent / default false) when merchant does not have DEFERRED_PAYMENT offering
- Existing proximity contract tests must not regress

## Implementation notes

`ProximityService.record()` already loads the merchant from DB. Add one call to `MerchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchant.id)` and check if `"DEFERRED_PAYMENT"` is in the result set. The repository is already used in `OrderService` via the same `merchant` module API.

Because `ProximityService` is in the `merchant` module, it can access `MerchantOfferingAssignmentRepository` directly — no cross-module event needed.
