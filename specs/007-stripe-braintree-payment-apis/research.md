# Research: Stripe & Braintree Payment APIs

**Date**: 2026-04-18  
**Phase**: 0 — Pre-design research  
**Feature**: specs/007-stripe-braintree-payment-apis/spec.md

---

## Decision 1: SDK vs raw WebClient for Stripe

**Decision**: Use the official `stripe-java` SDK, not a raw `WebClient` call.

**Rationale**: The existing `ExpoPushClient` uses raw `WebClient` because Expo has no official JVM SDK. Stripe's `stripe-java` SDK is a mature, well-tested library that handles authentication (secret key injection), request signing, idempotency keys, automatic retries, and API version pinning. Reimplementing this over raw HTTP would be a maintenance burden with no upside.

**Alternatives considered**:
- Raw `WebClient`: rejected — duplicates what the SDK already provides correctly.
- Stripe's newer `openapi`-generated client: not yet stable enough for production as of research date.

---

## Decision 2: SDK vs raw WebClient for Braintree

**Decision**: Use the official `braintree-java` SDK (`com.braintreepayments.gateway:braintree-java`, version `3.43.0`).

**Rationale**: Braintree authentication, environment routing (sandbox/production), client-token generation, and transaction submission are all encapsulated in the SDK. The SDK is synchronous/blocking; we wrap calls in `Schedulers.boundedElastic()` to stay WebFlux-compliant.

**Alternatives considered**:
- Raw `WebClient`: rejected — Braintree's auth model and multipart token encoding make raw HTTP unnecessarily complex.

---

## Decision 3: Stripe SDK version

**Decision**: Use `stripe-java` version `28.x` (latest stable at research date; `29.x` series is in beta).

**Rationale**: The prototype pins Stripe API version `2025-09-30.clover`. The `28.x` SDK series supports this API version. Once `29.x` reaches a stable release, upgrade is safe — Stripe Java SDK follows semantic versioning.

**Alternatives considered**:
- `29.3.0-beta.1`: available on Maven Central but explicitly beta — rejected for production use.

---

## Decision 4: Blocking SDK calls in reactive stack

**Decision**: Call both SDKs (Stripe, Braintree) on `Schedulers.boundedElastic()` via `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())`.

**Rationale**: Both SDKs are synchronous blocking libraries. Spring WebFlux's event loop must not be blocked. The existing `ExpoPushClient` uses `.block()` directly (acceptable in its context because it is called from a Modulith event listener, not a controller thread). For controller-facing calls, using `Mono.fromCallable` preserves reactive backpressure and keeps the event loop free.

**Alternatives considered**:
- `.block()` in service layer: works but blocks the event loop — rejected.
- Virtual threads (`spring.threads.virtual.enabled=true` is already set): virtual threads avoid thread starvation but the reactive contract still requires `Mono` return types from controllers; wrapping with `Mono.fromCallable` remains the cleanest path.

---

## Decision 5: Module boundary — one module or two

**Decision**: Two separate Spring Modulith modules: `payments.stripe` and `payments.braintree`.

**Rationale**: Stripe and Braintree have independent credentials, independent SDK lifecycles, and independent deployment concerns (e.g., one might be disabled in a given environment while the other is active). Keeping them in separate modules allows each to be independently disabled and independently tested. This mirrors the existing `payments.truelayer` pattern.

**Alternatives considered**:
- Single `payments.mobile` module: simpler initially, but mixes two independent external providers into one module with no clean boundary. Rejected.

---

## Decision 6: Persistence

**Decision**: No new database tables. Both modules are stateless.

**Rationale**: The feature is a credential-brokerage proxy. Transaction records live in Stripe's and Braintree's dashboards. The optional `invoiceId` field is passed through to the response for client-side correlation but is not persisted server-side. This avoids Liquibase migrations and keeps the modules simple.

**Alternatives considered**:
- Persist transaction IDs in a local audit table: adds complexity and Liquibase changesets. Can be added later if audit requirements emerge. Rejected for MVP.

---

## Decision 7: Braintree "not configured" behaviour

**Decision**: `BraintreeGateway` is an `@Bean` wrapped in a conditional (`@ConditionalOnProperty` on `braintree.merchant-id`). If unconfigured, the bean is absent and controllers return HTTP 503 with a structured JSON body.

**Rationale**: Mirrors the prototype's `if (!braintreeGateway)` guard. Spring's `@ConditionalOnProperty` is cleaner than a nullable bean — the controller receives an `Optional<BraintreeGateway>` via constructor injection and checks `isPresent()`.

**Alternatives considered**:
- Nullable bean with null checks in service: works but less idiomatic for Spring — rejected.
- Throw exception: unstructured 500 response — rejected.

---

## Decision 8: URL path design

**Decision**:
- Stripe: `POST /v1/payments/stripe/create-intent`
- Braintree: `POST /v1/payments/braintree/client-token`
- Braintree: `POST /v1/payments/braintree/checkout`

**Rationale**: Consistent with existing `/v1/payments/...` namespace. Provider-scoped sub-paths (`/stripe/`, `/braintree/`) keep routes self-documenting and avoid collisions. The prototype used `/api/payments/...` — we drop the `/api` prefix since Spring Boot's server context path is not `/api` in this project.

**Alternatives considered**:
- `/v1/stripe/...` at module root: less consistent with existing `payments.*` module convention — rejected.

---

## Resolved NEEDS CLARIFICATION

None — the spec contained no clarification markers.

---

## Dependencies to add

| Library | Coordinates | Version | Purpose |
|---------|-------------|---------|---------|
| stripe-java | `com.stripe:stripe-java` | `28.x` (latest stable) | Stripe PaymentIntent creation |
| braintree-java | `com.braintreepayments.gateway:braintree-java` | `3.43.0` | Braintree client token + sale |
