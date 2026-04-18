# Quickstart: Stripe & Braintree Payment APIs

**Feature**: specs/007-stripe-braintree-payment-apis  
**Date**: 2026-04-18

---

## Prerequisites

1. **Stripe account** with a secret key and a publishable key (sandbox keys for development).
2. **Braintree sandbox account** with merchant ID, public key, and private key (optional — the server degrades gracefully if unconfigured).
3. Java 25 and Gradle installed (or use `./gradlew`).
4. PostgreSQL running (existing app requirement — no new tables added by this feature).

---

## Environment Variables

Add the following to your local environment or `.env` file:

```bash
# Stripe
STRIPE_SECRET_KEY=sk_test_...
EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_test_...

# Braintree (all three required; if any is missing, Braintree endpoints return 503)
BRAINTREE_MERCHANT_ID=your_merchant_id
BRAINTREE_PUBLIC_KEY=your_public_key
BRAINTREE_PRIVATE_KEY=your_private_key
BRAINTREE_ENVIRONMENT=sandbox   # or 'production'
```

---

## Running the App

```bash
./gradlew bootRun
```

The new endpoints are available at:

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `http://localhost:8080/v1/payments/stripe/create-intent` | Stripe PaymentIntent |
| `POST` | `http://localhost:8080/v1/payments/braintree/client-token` | Braintree client token |
| `POST` | `http://localhost:8080/v1/payments/braintree/checkout` | Braintree checkout |

---

## Quick Smoke Tests

### Stripe — create a payment intent

```bash
curl -s -X POST http://localhost:8080/v1/payments/stripe/create-intent \
  -H "Content-Type: application/json" \
  -d '{"amount": 12.50, "currency": "eur"}' | jq .
```

Expected response:
```json
{
  "paymentIntent": "pi_xxx_secret_yyy",
  "publishableKey": "pk_test_..."
}
```

### Braintree — fetch a client token

```bash
curl -s -X POST http://localhost:8080/v1/payments/braintree/client-token \
  -H "Content-Type: application/json" | jq .
```

Expected response:
```json
{
  "clientToken": "eyJ2ZXJzaW9uIjoy..."
}
```

### Braintree — submit a checkout (sandbox nonce)

```bash
curl -s -X POST http://localhost:8080/v1/payments/braintree/checkout \
  -H "Content-Type: application/json" \
  -d '{"nonce": "fake-valid-nonce", "amount": 12.50, "currency": "EUR", "invoiceId": "INV-001"}' | jq .
```

Expected response (sandbox success):
```json
{
  "status": "succeeded",
  "transactionId": "dz89qrs4",
  "amount": "12.50",
  "currency": "EUR",
  "invoiceId": "INV-001"
}
```

### Braintree unconfigured (503)

If `BRAINTREE_MERCHANT_ID` is not set:
```bash
curl -s -X POST http://localhost:8080/v1/payments/braintree/client-token | jq .
# → {"error": "Braintree not configured on server"}  HTTP 503
```

---

## Running Tests

```bash
./gradlew test                  # unit tests only
./gradlew contractTest          # contract tests (no real Stripe/Braintree calls)
./gradlew check                 # unit + contract tests
```

Contract tests mock Stripe and Braintree beans under the `contract-test` Spring profile — no real API credentials required.

---

## OpenAPI / Swagger

Once the app is running, the Stripe and Braintree API groups appear in the Swagger UI at:

```
http://localhost:8080/swagger-ui.html
```

Look for the **Stripe** and **Braintree** groups in the dropdown.

---

## Module Verification

To verify Spring Modulith boundaries are not violated:

```bash
./gradlew test --tests "*.ModularityTest"
```
