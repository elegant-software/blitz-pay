# Data Model: Stripe & Braintree Payment APIs

**Date**: 2026-04-18  
**Feature**: specs/007-stripe-braintree-payment-apis

---

## Overview

The Stripe and Braintree modules are payment gateways, but with the merchant/branch domain the system now introduces **two new database tables** and modifies one existing table to support per-merchant and per-branch credential resolution.

### New / Modified Tables

| Table | Change | Purpose |
|-------|--------|---------|
| `merchant_applications` | MODIFY — add nullable payment credential columns | Merchant-level default Stripe/Braintree credentials |
| `merchant_branches` | NEW | Branch-level overrides; products and payments are scoped here |
| `merchant_products` | MODIFY — add nullable `merchant_branch_id` FK | Links products to a branch for payment routing and price derivation |

---

## Domain Entities

### MerchantApplication (modified)

Existing entity (`merchant_applications` table). New nullable columns added for payment credentials:

| New Column | Type | Nullable | Notes |
|------------|------|----------|-------|
| `stripe_secret_key` | `VARCHAR(512)` | Yes | Merchant-level Stripe secret; NEVER returned in responses |
| `stripe_publishable_key` | `VARCHAR(512)` | Yes | Merchant-level Stripe publishable key |
| `braintree_merchant_id` | `VARCHAR(255)` | Yes | Merchant-level Braintree merchant ID |
| `braintree_public_key` | `VARCHAR(255)` | Yes | Merchant-level Braintree public key |
| `braintree_private_key` | `VARCHAR(512)` | Yes | Merchant-level Braintree private key; NEVER returned in responses |
| `braintree_environment` | `VARCHAR(64)` | Yes | `sandbox` or `production`; defaults to `sandbox` when null |

---

### MerchantBranch (new entity)

Table: `merchant_branches` (schema `blitzpay`)

| Column | Type | Nullable | Constraints | Notes |
|--------|------|----------|-------------|-------|
| `id` | `UUID` | No | PK | |
| `merchant_application_id` | `UUID` | No | FK → `merchant_applications.id` | Owning merchant |
| `name` | `VARCHAR(255)` | No | | Human-readable branch name |
| `active` | `BOOLEAN` | No | default `true` | Soft-delete |
| `stripe_secret_key` | `VARCHAR(512)` | Yes | | Branch override; takes precedence over merchant default |
| `stripe_publishable_key` | `VARCHAR(512)` | Yes | | Branch override |
| `braintree_merchant_id` | `VARCHAR(255)` | Yes | | Branch override |
| `braintree_public_key` | `VARCHAR(255)` | Yes | | Branch override |
| `braintree_private_key` | `VARCHAR(512)` | Yes | | Branch override; NEVER returned in responses |
| `braintree_environment` | `VARCHAR(64)` | Yes | | Branch override; defaults to `sandbox` when null |
| `created_at` | `TIMESTAMP` | No | immutable | |
| `updated_at` | `TIMESTAMP` | No | | |

**Indexes**: `idx_merchant_branches_merchant_application_id`, `idx_merchant_branches_active`

---

### MerchantProduct (modified)

New column added to `merchant_products` table:

| New Column | Type | Nullable | Notes |
|------------|------|----------|-------|
| `merchant_branch_id` | `UUID` | Yes | FK → `merchant_branches.id`; required for new products, nullable for backward compat |

**Credential resolution order**: branch credentials → merchant default credentials → global `application.yml` values → HTTP 503.

---

---

## Stripe Module

### StripePaymentIntentRequest

Inbound from mobile client.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `amount` | `Double` | Conditional | > 0, finite | Decimal amount (e.g., `12.50`). Required unless `productId` is supplied. |
| `currency` | `String` | No | ISO 4217 | Defaults to `"eur"` if absent |
| `merchantId` | `UUID` | Yes | | Identifies the merchant; used for credential resolution |
| `branchId` | `UUID` | No | | If omitted, derived from `productId` context; 400 if unresolvable |
| `productId` | `UUID` | No | | If supplied, `product.unitPrice` is used as default `amount` |

### StripePaymentIntentResponse

Outbound to mobile client.

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `paymentRequestId` | `UUID` | Yes | Backend-generated correlation id for tracking payment status and webhooks |
| `clientSecret` | `String` | Yes | Stripe `client_secret` — preferred field for mobile Stripe SDK integration |
| `paymentIntent` | `String` | Yes | Stripe PaymentIntent id |
| `publishableKey` | `String` | Yes | Stripe publishable key resolved for the branch/merchant — safe to expose |

### Validation Rules

- `amount` must be a positive finite number; zero and negative values are rejected with HTTP 400.
- `currency` is lowercased before forwarding to Stripe. Unknown currencies are rejected by Stripe and surfaced as a 500 with a provider error message.
- If `productId` is supplied and an explicit `amount` differs from `product.unitPrice`, the discrepancy is logged at WARN level.
- `merchantId` is required; 400 if absent.
- If neither `branchId` nor `productId` is provided, return 400 (`branch cannot be resolved`).
- If resolved credentials are absent at both branch and merchant level, return 503.

---

## Braintree Module

### BraintreeClientTokenRequest

Inbound from mobile client (body of `POST /v1/payments/braintree/client-token`).

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `merchantId` | `UUID` | Yes | Used for credential resolution |
| `branchId` | `UUID` | No | Optional; branch credentials take precedence if present |

### BraintreeClientTokenResponse

Outbound to mobile client.

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `clientToken` | `String` | Yes | Base64-encoded JSON token for Braintree mobile SDK |

### BraintreeCheckoutRequest

Inbound from mobile client.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `nonce` | `String` | Yes | Non-blank | Single-use payment method nonce from mobile Braintree SDK |
| `amount` | `Double` | Conditional | > 0, finite | Required unless `productId` is supplied |
| `currency` | `String` | No | ISO 4217 | Defaults to `"EUR"`; used in response for display only |
| `merchantId` | `UUID` | Yes | | Required for credential resolution |
| `branchId` | `UUID` | No | | Optional; derived from `productId` if absent |
| `productId` | `UUID` | No | | If supplied, `product.unitPrice` is used as default `amount` |
| `invoiceId` | `String` | No | | Optional invoice reference for reconciliation |

### BraintreeCheckoutResponse

Outbound to mobile client.

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `status` | `String` | Yes | `"succeeded"` or `"failed"` |
| `transactionId` | `String` | When `status = "succeeded"` | Braintree transaction ID |
| `amount` | `String` | When `status = "succeeded"` | Formatted amount string (e.g., `"12.50"`) |
| `currency` | `String` | When `status = "succeeded"` | Echo of request currency |
| `merchantId` | `UUID` | Yes | Echo of merchant reference |
| `branchId` | `UUID` | When resolved | Echo of resolved branch reference |
| `invoiceId` | `String` | When provided in request | Echo of invoice reference |
| `message` | `String` | When `status = "failed"` | Human-readable decline reason |
| `code` | `String` | When `status = "failed"` | Processor response code (may be absent) |

### Error Responses (both modules)

| HTTP Status | Condition | Body shape |
|-------------|-----------|------------|
| 400 | Invalid request (missing nonce, invalid amount, unresolvable branch) | `{"error": "<message>"}` |
| 503 | No Stripe/Braintree credentials resolved at branch or merchant level | `{"error": "Payment provider not configured"}` |
| 500 | Provider-side failure | `{"error": "<provider message>"}` |

### Validation Rules

- `nonce` must be non-blank; rejected with HTTP 400 if missing.
- `amount` must be a positive finite number; zero and negative values rejected with HTTP 400.
- `invoiceId` is optional; echoed in success response without further validation.
- `merchantId` is required on all endpoints; 400 if absent.
- If neither `branchId` nor `productId` is provided, return 400 (`branch cannot be resolved`).
- If `productId` supplied with explicit `amount` override that differs from `product.unitPrice`, log at WARN.

---

## MerchantBranch Management Endpoint

### CreateBranchRequest

Inbound to `POST /v1/merchants/{merchantId}/branches`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | `String` | Yes | Human-readable branch name |
| `stripeSecretKey` | `String` | No | Branch-level Stripe secret; stored encrypted-at-rest |
| `stripePublishableKey` | `String` | No | Branch-level publishable key |
| `braintreeMerchantId` | `String` | No | Branch-level Braintree merchant ID |
| `braintreePublicKey` | `String` | No | |
| `braintreePrivateKey` | `String` | No | NEVER returned in responses |
| `braintreeEnvironment` | `String` | No | `sandbox` or `production`; defaults to `sandbox` |

### BranchResponse

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` | |
| `merchantId` | `UUID` | |
| `name` | `String` | |
| `active` | `Boolean` | |
| `hasStripeCredentials` | `Boolean` | True if branch-level Stripe credentials are set; keys never returned |
| `hasBraintreeCredentials` | `Boolean` | True if branch-level Braintree credentials are set; keys never returned |
| `createdAt` | `Instant` | |
| `updatedAt` | `Instant` | |

---

## State Transitions

Neither module maintains server-side state. The lifecycle of a payment is:

```
Mobile App
  │
  ├── [Stripe] POST /v1/payments/stripe/create-intent
  │     └── Server → Stripe API → returns client_secret
  │           └── Mobile uses client_secret to complete payment in Stripe SDK
  │
  └── [Braintree]
        ├── POST /v1/payments/braintree/client-token
        │     └── Server → Braintree API → returns clientToken
        │           └── Mobile SDK initialises Braintree UI with token
        │
        └── POST /v1/payments/braintree/checkout
              └── Server → Braintree API (sale) → returns success/failure
```
