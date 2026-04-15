# Feature Specification: Push Notifications for Payment Status

**Feature Branch**: `006-push-notifications`  
**Created**: 2026-04-15  
**Status**: Draft  
**Input**: User description: "I want to implement push notification on true layer webhook on payment_executed / payment_settled using Expo Push (APNs + FCM) and having fallback end point GET /v1/payments/{paymentRequestId} for immune to push latency, I think there is no need to server side events any more and that part could be removed"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Payer receives push notification on payment completion (Priority: P1)

A payer initiates a QR-code-based payment from the merchant's point-of-sale. After scanning the QR code and authorizing the payment in their banking app, the payer's mobile app displays a confirmation screen as soon as the payment settlement completes on the backend — without requiring the payer to keep the app in the foreground or poll manually.

**Why this priority**: This is the core user value of the feature. Today payers rely on an always-open browser/app session streaming server-sent events; replacing that with push notifications enables background completion, removes a whole class of connection-drop bugs, and is the minimum viable slice of this initiative.

**Independent Test**: A test payer app with a registered push token completes a payment against a sandbox merchant. The webhook simulator fires `payment_settled`; the app receives a push notification within the expected latency window and transitions to the "Paid" state, even when the app is backgrounded.

**Acceptance Scenarios**:

1. **Given** a payer has an active payment request and a registered device push token, **When** the payment gateway reports `payment_settled` for that payment, **Then** the payer's device receives a push notification identifying the payment request and its final status.
2. **Given** a payer's payment is executed but not yet settled, **When** the gateway reports `payment_executed`, **Then** the payer's device receives a push notification indicating the payment is progressing.
3. **Given** a payer's app is backgrounded or closed, **When** a terminal payment status arrives, **Then** the notification is still delivered to the device via the platform push service.

---

### User Story 2 - Reliable status fallback via direct query (Priority: P1)

Whenever a payer's app is uncertain about the current state of a payment (app just opened, push not yet received, notification was dismissed, delivery delayed), it can fetch the authoritative current status directly so the payer never sees a stale screen.

**Why this priority**: Push delivery is best-effort — notifications can be delayed, dropped, or silenced by the OS. Without a pull-based fallback, the feature would regress reliability versus today's SSE flow. This must ship alongside the push path, not later.

**Independent Test**: With push notifications disabled at the OS level, the payer app queries the payment status endpoint on foreground and after every payment-related user action. The app consistently shows the correct final status within the fallback polling window.

**Acceptance Scenarios**:

1. **Given** a payer knows a payment request identifier, **When** they request the current payment state, **Then** they receive the authoritative current status, including terminal states (settled, failed, expired) and non-terminal states (pending, executed).
2. **Given** a payment has already reached a terminal state, **When** the status is queried minutes or hours later, **Then** the same terminal status is returned consistently.
3. **Given** an unknown or unauthorized payment request identifier is queried, **When** the caller lacks permission or the request does not exist, **Then** the system responds with an appropriate not-found / forbidden outcome without leaking information about other payments.

---

### User Story 3 - Device registration and lifecycle management (Priority: P2)

A payer app registers a push token with the backend when the app is installed or reinstalled, and unregisters or replaces that token when it becomes invalid (uninstall, OS push-token rotation, user logout).

**Why this priority**: Without registration, Story 1 cannot function. It is split as a separate story so the registration surface can be tested and deployed independently of webhook processing, and because it introduces its own privacy / identity concerns.

**Independent Test**: A test app submits a new push token; the backend accepts and stores it. Submitting the same token again is idempotent. Submitting a replacement token for the same installation supersedes the old one. An invalid-token response from the push provider causes the token to be retired.

**Acceptance Scenarios**:

1. **Given** a payer's app has obtained a push token from its platform, **When** it registers the token with the backend against a payment request (or payer identity), **Then** subsequent payment status events for that payer result in notifications delivered to that token.
2. **Given** a device's token is reported as invalid by the push provider, **When** the backend attempts delivery, **Then** the token is marked unusable and no further attempts are made against it.
3. **Given** a device registers a replacement token, **When** the replacement is stored, **Then** the previous token for the same installation is no longer used.

---

### User Story 4 - Remove obsolete SSE streaming path (Priority: P3)

Once push + pull fallback is in place and validated, the server-sent events streaming surface used today for QR payment status updates is decommissioned. Clients no longer need to maintain long-lived streaming connections.

**Why this priority**: Cleanup, not new value. Must land after Stories 1–3 are in production and the mobile client has migrated; otherwise it breaks existing consumers.

**Independent Test**: After all clients are updated, confirm there are no active consumers of the SSE endpoint over a representative observation window. Remove the endpoint and associated plumbing; regression-test the QR payment flow end-to-end with push + fallback only.

**Acceptance Scenarios**:

1. **Given** all supported client versions use push + status-query for payment updates, **When** the SSE endpoint is removed, **Then** the QR payment flow continues to function end-to-end with the same or better perceived latency.
2. **Given** a legacy client attempts to connect to the removed streaming endpoint, **When** the request is made, **Then** the system responds with a clear deprecation / not-found signal rather than hanging the client.

---

### Edge Cases

- **Duplicate webhook deliveries** — the gateway may deliver the same status event more than once; the payer must not receive duplicate notifications for the same status transition.
- **Out-of-order webhook deliveries** — `payment_settled` may arrive before `payment_executed`; the system must not regress a terminal status back to a non-terminal one.
- **Push provider outage** — when the push provider is unavailable or slow, the status query fallback must continue to reflect the authoritative state.
- **Invalid / expired push tokens** — delivery failures must not block subsequent notifications to other devices, and stale tokens must be retired.
- **Payer has multiple devices** — all registered, valid devices for the payer should be notified.
- **Payer has no registered device at webhook time** — the status must still be persisted so a later status query returns the correct state.
- **Notification permission revoked by the OS** — the app must still be able to retrieve status via the query endpoint.
- **Unknown or already-terminal payment at webhook time** — handled idempotently without error loops.
- **Querying a payment the caller is not entitled to see** — must not leak existence or details.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST send a push notification to every valid registered device associated with a payment request when that payment's status transitions to `payment_executed` or `payment_settled` based on the payment gateway webhook.
- **FR-002**: System MUST treat incoming payment-gateway webhook deliveries as idempotent so that duplicate deliveries of the same status event produce at most one user-visible notification.
- **FR-003**: System MUST NOT regress a payment's recorded status from a terminal state (e.g., settled, failed) back to a non-terminal state even if webhook events arrive out of order.
- **FR-004**: System MUST expose a status-query endpoint `GET /v1/payments/{paymentRequestId}` that returns the authoritative current status of a payment request, including terminal and non-terminal states and the time of the last status change.
- **FR-005**: System MUST authorize status queries so that a caller can only retrieve status for payment requests they are entitled to see, and MUST respond with a non-information-leaking not-found for unauthorized or unknown identifiers.
- **FR-006**: System MUST allow a payer's mobile app to register, replace, and unregister a push token linked to a payment request or payer identity, with idempotent behavior on repeated registration of the same token.
- **FR-007**: System MUST retire a push token when the push provider indicates it is invalid or unregistered, and MUST NOT attempt further deliveries to that token.
- **FR-008**: System MUST support push delivery to both iOS and Android devices via a unified push delivery provider.
- **FR-009**: System MUST persist payment status transitions durably so that status-query responses are correct even across restarts and in the absence of a live streaming connection.
- **FR-010**: Push notifications MUST carry enough context for the client to identify the payment request and its new status without a round-trip to the backend being strictly required for display.
- **FR-011**: System MUST log every outbound push attempt with its outcome (accepted, rejected, retried) for operational observability, without logging personally identifying payment details beyond what is necessary.
- **FR-012**: System MUST continue to publish internal payment-update domain events so that non-mobile consumers (if any) remain decoupled from the transport choice.
- **FR-013**: System MUST decommission the existing server-sent events streaming surface for QR payment status updates once the push + status-query path is active, returning an explicit deprecation response to any residual clients before full removal.
- **FR-014**: System MUST verify the authenticity of inbound payment-gateway webhooks before acting on them (unchanged from today's behavior).
- **FR-015**: System MUST retry transient push-delivery failures with bounded backoff and give up cleanly on permanent failures, without blocking processing of other webhooks.

### Key Entities *(include if feature involves data)*

- **Payment Request**: An existing concept representing an amount to be collected from a payer via the gateway. Gains an authoritative, durably-stored current status and a last-updated timestamp queryable by identifier.
- **Payment Status Event**: A recorded transition (e.g., `payment_executed`, `payment_settled`, `payment_failed`) derived from a gateway webhook, carrying the payment request identifier, new status, and event time. Deduplicated by gateway-provided event identifier.
- **Device Registration**: The association between a payer (or payment request) and a mobile device's push token, including platform (iOS/Android), creation time, and validity flag. Uniquely identifies where to deliver a notification.
- **Push Delivery Attempt**: A record of an outbound notification attempt — target token, payload summary, outcome, and timestamp — used for observability and retry decisions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For at least 95% of successful payments, the payer's device displays the terminal payment status within 5 seconds of the gateway reporting settlement, measured end-to-end in production.
- **SC-002**: For 100% of payments where the push notification is delayed, dropped, or the OS suppresses it, the payer sees the correct terminal status within 2 seconds of opening or foregrounding the app, via the status-query fallback.
- **SC-003**: Duplicate push notifications for the same status transition occur in under 0.1% of payments.
- **SC-004**: Zero incidents in which a payer's app displays a non-terminal status for a payment that has already reached a terminal status for more than 10 seconds after app foreground.
- **SC-005**: Invalid or expired push tokens are retired within 1 delivery attempt of the push provider reporting them as unusable; no token is retried indefinitely.
- **SC-006**: After SSE decommission, the proportion of long-lived streaming connections to the payments service drops to zero, and the QR payment completion rate is equal to or better than the pre-change baseline.
- **SC-007**: Mobile battery and data-usage impact attributable to payment-status updates decreases relative to the SSE baseline, as measured by a representative client instrumentation sample.

## Assumptions

- The existing payment-gateway webhook pipeline (signature verification, event ingestion) is reused; this feature adds a consumer, it does not replace the ingestion path.
- A single managed push delivery provider is used as the unified channel to both iOS and Android devices; gateway-specific credentials (APNs keys, FCM credentials) are configured in the provider's console, not in this service.
- Each payer is identified by the existing payment-request authorization mechanism used today (no new auth system is introduced by this feature).
- Device registration is tied at minimum to a payment request identifier; broader "payer identity" registration is acceptable but not required for the P1 slice.
- The status-query endpoint returns the same status vocabulary already used internally for payment states.
- The SSE removal (User Story 4) is gated on rollout of updated mobile clients; removal timing is a deployment decision, not a spec constraint.
- Retention of push delivery attempt logs follows the project's existing operational-log retention policy.
- Notifications are informational; the authoritative source of truth for a payment's status is always the backend, queryable via the status endpoint.
