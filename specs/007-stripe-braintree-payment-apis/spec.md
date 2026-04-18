# Feature Specification: Stripe & Braintree Payment APIs

**Feature Branch**: `007-stripe-braintree-payment-apis`  
**Created**: 2026-04-18  
**Status**: Draft  
**Input**: User description: "Look at /Users/mehdi/MyProject/blitz-pay-prototype/mobile/server.ts I want to have the same APIs implemented here"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Card Payment via Stripe (Priority: P1)

A mobile user chooses to pay by card. The app needs the server to securely initiate a payment session with the card payment provider and return the credentials the mobile SDK needs to render the payment form and collect card details.

**Why this priority**: Card payment is the most common payment method and is the baseline revenue-enabling capability. Without it, the app cannot process any payments.

**Independent Test**: Can be tested end-to-end by submitting a payment amount and verifying the response contains the data a mobile payment form needs to proceed with card entry.

**Acceptance Scenarios**:

1. **Given** a valid amount and currency, **When** the mobile app requests a card payment session, **Then** the server returns a secure session token and the public key needed for the mobile payment SDK.
2. **Given** an invalid or missing amount, **When** the mobile app submits the request, **Then** the server returns a descriptive error and does not initiate a payment session.
3. **Given** a network or provider outage, **When** the mobile app requests a card payment session, **Then** the server returns an error indicating the service is temporarily unavailable.

---

### User Story 2 - PayPal / Digital Wallet Payment via Braintree (Priority: P2)

A mobile user chooses to pay via PayPal or another digital wallet supported by Braintree. The app first retrieves a short-lived client token from the server, then uses that token to present the Braintree drop-in UI, and finally submits the resulting payment nonce back to the server to complete the transaction.

**Why this priority**: PayPal is a widely used alternative to card payments. Supporting it broadens the addressable user base and reduces checkout abandonment for users who prefer digital wallets.

**Independent Test**: Can be tested by (1) fetching a client token and verifying it is valid base64-encoded JSON, and (2) submitting a sandbox nonce with an amount and verifying a successful transaction ID is returned.

**Acceptance Scenarios**:

1. **Given** Braintree is configured, **When** the mobile app requests a client token, **Then** the server returns a valid short-lived token for the mobile SDK to initialise the payment UI.
2. **Given** a valid payment nonce and amount, **When** the mobile app submits the Braintree checkout, **Then** the server processes the transaction and returns a success status with a transaction reference.
3. **Given** a declined or invalid nonce, **When** the mobile app submits the Braintree checkout, **Then** the server returns a failure status with a human-readable reason rather than an error code.
4. **Given** Braintree is not configured on the server, **When** the mobile app requests a client token or submits a checkout, **Then** the server returns a clear "service not available" response rather than crashing.

---

### User Story 3 - Invoice-linked Payments (Priority: P3)

When a user pays an invoice, the payment can be linked to that invoice at the time of processing so that accounting and reconciliation can match payments to outstanding invoices.

**Why this priority**: Invoice linkage is an audit and reconciliation need rather than a checkout blocker. It adds business value for record-keeping but does not prevent payments from being processed without it.

**Independent Test**: Can be tested by submitting a Braintree checkout with an invoice reference and verifying the response includes the invoice identifier echoed back.

**Acceptance Scenarios**:

1. **Given** a valid nonce, amount, and invoice reference, **When** the mobile app submits checkout, **Then** the server processes the payment and includes the invoice reference in the success response.
2. **Given** a checkout request without an invoice reference, **When** the server processes it, **Then** the payment proceeds normally and the missing invoice reference does not cause an error.

---

### Edge Cases

- What happens when the amount is zero or negative?
- What happens when the currency is unsupported by the provider?
- How does the system handle a Braintree client token request when the provider is temporarily unreachable?
- What happens when a Braintree nonce is replayed (submitted more than once)?
- How does the system behave under high concurrency (many simultaneous payment session requests)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose an endpoint that accepts a monetary amount and currency, initiates a card payment session with the Stripe provider, and returns the session credentials required by the mobile Stripe SDK.
- **FR-002**: System MUST expose an endpoint that generates and returns a short-lived Braintree client token for the mobile Braintree SDK to initialise its payment UI.
- **FR-003**: System MUST expose an endpoint that accepts a Braintree payment nonce and amount, submits the transaction to Braintree for immediate settlement, and returns the outcome (success with transaction ID, or failure with reason).
- **FR-004**: System MUST validate that the payment amount is a positive finite number before forwarding to any payment provider.
- **FR-005**: System MUST return a structured error response (not an unhandled exception) for all provider-side failures.
- **FR-006**: System MUST return a 503 response when a Braintree endpoint is called but Braintree is not configured, rather than throwing an internal error.
- **FR-007**: System MUST support an optional invoice identifier on Braintree checkout requests so that payments can be associated with specific invoices for reconciliation.
- **FR-008**: All endpoints MUST be accessible over HTTPS and MUST NOT expose secret provider credentials in any response body.
- **FR-009**: Stripe payment capability and Braintree payment capability MUST be implemented as independent, separately configurable modules; disabling or misconfiguring one MUST NOT affect the availability or behaviour of the other.
- **FR-010**: The system MUST log each payment event (payment session initiation, checkout success, checkout failure) including: provider name, transaction or session ID, amount, and currency. Secret keys, payment nonces, card details, and private credentials MUST never appear in log output.

### Key Entities

- **Card Payment Session**: A short-lived authorisation context created with the card provider, identified by a client secret and associated with a specific amount and currency.
- **Braintree Client Token**: A short-lived token issued by the server to allow the mobile SDK to communicate with Braintree; scoped to the merchant account.
- **Payment Nonce**: A single-use token generated client-side by the Braintree mobile SDK representing a payment method authorisation.
- **Transaction**: A completed or declined payment record returned by the provider, carrying a unique identifier, amount, currency, and settlement status.
- **Invoice Reference**: An optional identifier linking a payment transaction to a specific invoice in the system.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Mobile clients can initiate a card payment session and receive all required credentials in under 3 seconds under normal load.
- **SC-002**: Mobile clients can fetch a Braintree client token and receive it in under 2 seconds under normal load.
- **SC-003**: A Braintree checkout request is processed and a result returned to the mobile client in under 5 seconds under normal load.
- **SC-004**: All three payment endpoints return structured, actionable error responses 100% of the time — no raw stack traces or unhandled exceptions reach the client.
- **SC-005**: Provider credentials (secret keys, private keys) are never present in any API response body or server log output.
- **SC-006**: The system correctly handles Braintree being unconfigured without crashing, as verified by an explicit "service unavailable" response in 100% of such cases.
- **SC-007**: Every payment event (session creation, checkout success, checkout failure) produces a structured log entry containing provider, transaction/session ID, amount, and currency — with zero occurrences of secret keys, nonces, or card data in log output.

## Clarifications

### Session 2026-04-18

- Q: Should Stripe and Braintree payment capabilities be implemented in the same module or separate independent modules? → A: Separate, independently configurable modules — disabling one must not affect the other. (FR-009 added)
- Q: What events and fields should the server log for payment operations, given PCI-safe audit requirements? → A: Log provider, transaction ID, amount, and currency per event; never log nonce, card details, or credentials. (FR-010 added)

## Assumptions

- The mobile client is an Expo/React Native application that uses the Stripe and Braintree mobile SDKs; the server's role is credential brokerage and transaction submission, not UI rendering.
- The default currency is EUR if not specified by the mobile client.
- Amounts are provided by the mobile client as decimal numbers (e.g., 12.50 representing €12.50); the server is responsible for converting to the provider's expected unit where needed.
- Braintree transactions are submitted for immediate settlement (no separate capture step).
- Authentication and authorisation of mobile clients calling these endpoints is handled by an existing auth layer and is out of scope for this feature.
- The Stripe publishable key is available as a server-side configuration value and is safe to return to mobile clients.
- Sandbox/test mode is used during development; production credentials are managed via environment configuration without code changes.
