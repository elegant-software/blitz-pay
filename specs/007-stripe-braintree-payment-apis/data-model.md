# Data Model: Stripe & Braintree Payment APIs

**Date**: 2026-04-18  
**Feature**: specs/007-stripe-braintree-payment-apis

---

## Overview

Both modules are **stateless gateways** — no new database tables are introduced. All persistent state lives in the Stripe and Braintree dashboards.

The data model below describes the request/response data structures flowing through the system.

---

## Stripe Module

### StripePaymentIntentRequest

Inbound from mobile client.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `amount` | `Double` | Yes | > 0, finite | Decimal amount (e.g., `12.50` for €12.50) |
| `currency` | `String` | No | ISO 4217, e.g. `"eur"` | Defaults to `"eur"` if absent |

### StripePaymentIntentResponse

Outbound to mobile client.

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `paymentIntent` | `String` | Yes | Stripe `client_secret` — used by mobile Stripe SDK |
| `publishableKey` | `String` | Yes | Stripe publishable key — safe to expose to clients |

### Validation Rules

- `amount` must be a positive finite number; zero and negative values are rejected with HTTP 400.
- `currency` is lowercased before forwarding to Stripe. Unknown currencies are rejected by Stripe and surfaced as a 500 with a provider error message.

---

## Braintree Module

### BraintreeClientTokenResponse

Outbound to mobile client (response to `POST /v1/payments/braintree/client-token`).

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `clientToken` | `String` | Yes | Base64-encoded JSON token for Braintree mobile SDK |

### BraintreeCheckoutRequest

Inbound from mobile client.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `nonce` | `String` | Yes | Non-blank | Single-use payment method nonce from mobile Braintree SDK |
| `amount` | `Double` | Yes | > 0, finite | Decimal amount (e.g., `12.50`) |
| `currency` | `String` | No | ISO 4217 | Defaults to `"EUR"`; used in response for display only — Braintree's currency is set at merchant account level |
| `invoiceId` | `String` | No | — | Optional invoice reference for client-side reconciliation |

### BraintreeCheckoutResponse

Outbound to mobile client.

| Field | Type | Always present | Notes |
|-------|------|----------------|-------|
| `status` | `String` | Yes | `"succeeded"` or `"failed"` |
| `transactionId` | `String` | When `status = "succeeded"` | Braintree transaction ID |
| `amount` | `String` | When `status = "succeeded"` | Formatted amount string (e.g., `"12.50"`) |
| `currency` | `String` | When `status = "succeeded"` | Echo of request currency |
| `invoiceId` | `String` | When provided in request | Echo of invoice reference |
| `message` | `String` | When `status = "failed"` | Human-readable decline reason |
| `code` | `String` | When `status = "failed"` | Processor response code (may be absent) |

### Error Responses (both modules)

| HTTP Status | Condition | Body shape |
|-------------|-----------|------------|
| 400 | Invalid request (missing nonce, invalid amount) | `{"error": "<message>"}` |
| 503 | Braintree not configured | `{"error": "Braintree not configured on server"}` |
| 500 | Provider-side failure | `{"error": "<provider message>"}` |

### Validation Rules

- `nonce` must be non-blank; rejected with HTTP 400 if missing.
- `amount` must be a positive finite number; zero and negative values rejected with HTTP 400.
- `invoiceId` is optional; if present it is echoed in the success response and not validated further.

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
