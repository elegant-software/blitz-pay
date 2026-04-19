# Implementation Plan: Merchant Registration (Skip Onboarding)

**Branch**: `001-merchant-onboarding` | **Date**: 2026-03-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-merchant-onboarding/spec.md`

## Summary

The user decided to skip the full multi-step merchant onboarding workflow (KYB, AML screening, manual review) for now. The immediate goal is a **direct merchant registration** endpoint: `POST /v1/merchants` creates a `MerchantApplication` and immediately sets it to `ACTIVE`, bypassing verification, screening, risk assessment, and decisioning stages. A `GET /v1/merchants/{merchantId}` endpoint retrieves the registered merchant. All compliance workflow steps (User Stories 2 and 3 from the spec) are deferred.

---

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, Spring Data JPA, Hibernate
**Storage**: PostgreSQL 16 — `ddl-auto: update`, no migration framework. All required tables already exist.
**Testing**: JUnit 5 + Mockito Kotlin (unit); WebTestClient (contract tests)
**Target Platform**: Linux server (containerized)
**Project Type**: Web service (modular monolith via Spring Modulith)
**Performance Goals**: Not specified for this scope
**Constraints**: No new DB tables. Stay within the `merchant` Spring Modulith module. No cross-module coupling.
**Scale/Scope**: Single merchant registration endpoint + retrieval

---

## Constitution Check

The project constitution (`constitution.md`) uses placeholder template content and has no ratified principles. Evaluation is based on CLAUDE.md architectural rules:

| Rule | Status |
|------|--------|
| Each module owns its API, implementation, persistence, and events | PASS — all new code stays in `merchant/` |
| Cross-module communication via events, not direct bean coupling | PASS — no cross-module calls introduced |
| `@NamedInterface` for sub-package exposure | PASS — `MerchantGateway` already uses `@NamedInterface`; controller lives in `api/` |
| No migration framework — Hibernate `ddl-auto: update` | PASS — no new tables |
| Spring WebFlux (reactive) | NOTE — existing merchant code uses JPA (blocking). Controller will use `Mono.fromCallable` wrapping, consistent with existing pattern (see invoice module) |
| Contract tests under `contract-test` profile | PASS — controller contract test required |

**Gate result**: PASS. No violations to justify.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-merchant-onboarding/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── contracts/
│   └── merchant-registration.md  ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code Changes

```text
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── domain/
│   └── MerchantOnboardingLifecycle.kt    MODIFY — add DRAFT→ACTIVE transition
│   └── MerchantApplication.kt            MODIFY — add registerDirect() method
├── application/
│   └── MerchantRegistrationService.kt    CREATE — register + findById
└── api/
    ├── MerchantOnboardingModels.kt       MODIFY — add RegisterMerchantRequest
    └── MerchantController.kt             CREATE — POST /v1/merchants, GET /v1/merchants/{id}

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantControllerTest.kt             CREATE — WebTestClient contract tests

src/test/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantRegistrationServiceTest.kt    CREATE — unit tests for service
```

---

## Implementation Decisions

### 1. Lifecycle: `DRAFT → ACTIVE`

`MerchantOnboardingLifecycle` transition map gains `ACTIVE` in the `DRAFT` allowed-targets set.

```kotlin
MerchantOnboardingStatus.DRAFT to setOf(
    MerchantOnboardingStatus.SUBMITTED,
    MerchantOnboardingStatus.ACTIVE      // ← new: direct registration path
),
```

### 2. Domain Method: `MerchantApplication.registerDirect()`

```kotlin
fun registerDirect(activatedAt: Instant = Instant.now()) {
    MerchantOnboardingLifecycle.requireTransition(status, MerchantOnboardingStatus.ACTIVE)
    status = MerchantOnboardingStatus.ACTIVE
    submittedAt = activatedAt
    touch(activatedAt)
}
```

This keeps lifecycle enforcement intact — the same `requireTransition` guard applies.

### 3. `MerchantRegistrationService`

```kotlin
@Service
@Transactional
class MerchantRegistrationService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantAuditTrail: MerchantAuditTrail,
    private val merchantObservabilitySupport: MerchantObservabilitySupport
) {
    fun register(request: RegisterMerchantRequest): MerchantApplication { ... }
    fun findById(merchantId: UUID): MerchantApplication { ... }
}
```

`register()` logic:
1. Check for duplicate active merchant by `registrationNumber`.
2. Generate `applicationReference` as `"BLTZ-" + UUID.randomUUID().toString().take(8).uppercase()`.
3. Create `MerchantApplication` with `DRAFT` status.
4. Call `application.registerDirect(now)`.
5. Save and audit.

### 4. `MerchantController`

```kotlin
@RestController
@RequestMapping("/v1/merchants")
class MerchantController(
    private val merchantRegistrationService: MerchantRegistrationService
) {
    @PostMapping
    fun register(@RequestBody request: RegisterMerchantRequest): Mono<ResponseEntity<MerchantApplicationResponse>>

    @GetMapping("/{merchantId}")
    fun get(@PathVariable merchantId: UUID): Mono<ResponseEntity<MerchantApplicationResponse>>
}
```

Wraps blocking JPA calls in `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())`, consistent with how the reactive layer handles JPA in this project.

### 5. `RegisterMerchantRequest` (added to `MerchantOnboardingModels.kt`)

```kotlin
data class RegisterMerchantRequest(
    val businessProfile: MerchantBusinessProfileRequest,
    val primaryContact: MerchantPrimaryContactRequest
)
```

### 6. Error Handling

| Exception | HTTP Status |
|-----------|-------------|
| `IllegalArgumentException` (duplicate / validation) | 400 or 409 |
| `NoSuchElementException` | 404 |

Spring's default `ResponseEntityExceptionHandler` or the project's existing error handling should cover `IllegalArgumentException`. Check existing error handler first.

---

## Deferred (Out of Scope)

- User Story 2: Status tracking / merchant portal
- User Story 3: Internal review, decisioning, activation workflow
- Supporting materials upload
- AML/KYB screening integration
- Notification system
- GDPR data subject rights endpoints
- Monitoring record management

These are preserved in the spec and tasks.md for future sprints.

---

## Phase 0 Output

→ [research.md](research.md)

## Phase 1 Output

→ [data-model.md](data-model.md)
→ [contracts/merchant-registration.md](contracts/merchant-registration.md)

---

## Product Catalog Feature (added 2026-04-19)

### Summary

Each active merchant can manage a product catalog. Every product has a name, a unit price (decimal ≥ 0 in the merchant's implicit currency), an optional image URL (object storage), and a soft-delete `active` flag. Multi-tenancy is enforced via two independent layers: Hibernate `@Filter` at the application layer and PostgreSQL RLS at the database layer — following Thomas Vitalle's discriminator-based Spring Boot multi-tenancy pattern.

### Technical Context

**Tenant key**: `MerchantApplication.id` (UUID) → `merchant_application_id` column on `merchant_products`
**Multi-tenancy primary**: Hibernate `@FilterDef` / `@Filter` (`tenantFilter`) enabled per request in service layer
**Multi-tenancy safety net**: PostgreSQL RLS policy reading `app.current_merchant_id` session variable (set via `SET LOCAL` at transaction start)
**Reactive propagation**: `MerchantTenantContext` stored in Reactor `ContextView`; transferred to Hibernate session inside `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())`
**Image storage**: S3-compatible object storage; product record stores resolved HTTPS URL
**Price**: `DECIMAL(12,4)` with `CHECK (unit_price >= 0)`; zero = free/sample product

### Constitution Check (Product Catalog)

| Rule | Status |
|------|--------|
| Module ownership — all code stays in `merchant/` | PASS |
| Cross-module communication via events | PASS — no cross-module calls |
| Hibernate `ddl-auto` — schema managed by Liquibase | PASS — two new changesets (table + RLS) |
| Spring WebFlux reactive | PASS — Reactor context propagation for tenant; blocking JPA wrapped in `boundedElastic` |
| Contract tests required | PASS — `MerchantProductControllerTest` required |

**Gate result**: PASS.

### Source Code Changes

```text
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── domain/
│   └── MerchantProduct.kt                        CREATE — @FilterDef/@Filter entity
├── application/
│   └── MerchantProductService.kt                 CREATE — CRUD + tenant filter activation
├── web/
│   └── MerchantProductController.kt              CREATE — POST/GET/PUT/DELETE /v1/merchants/{id}/products
│   └── MerchantTenantFilter.kt                   CREATE — WebFilter writing tenant UUID to Reactor context
├── repository/
│   └── MerchantProductRepository.kt              CREATE — JpaRepository with tenant-safe finders
└── api/
    └── MerchantProductModels.kt                  CREATE — request/response DTOs

src/main/resources/db/changelog/
└── 20260419-003-merchant-products.sql            CREATE — table DDL + RLS policy (changesets 008, 009)

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantProductControllerTest.kt              CREATE — WebTestClient contract tests

src/test/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantProductServiceTest.kt                 CREATE — unit tests (filter activation, tenant isolation)
```

### Implementation Decisions

#### 1. Hibernate Filter Activation

Before every repository call the service enables the filter on the current Hibernate session and sets the `SET LOCAL app.current_merchant_id` RLS variable in the same transaction:

```kotlin
private fun enableTenantFilter(merchantId: UUID) {
    val session = entityManager.unwrap(Session::class.java)
    session.enableFilter("tenantFilter").setParameter("merchantId", merchantId)
    entityManager.createNativeQuery("SET LOCAL app.current_merchant_id = :mid")
        .setParameter("mid", merchantId.toString())
        .executeUpdate()
}
```

The filter is scoped to the session and therefore to the transaction — no thread-local leakage risk.

#### 2. Reactor Context → Hibernate Session Bridge

Product service methods are `suspend`/`Mono`-based. Tenant ID is read from Reactor context and passed explicitly to `enableTenantFilter` inside the `boundedElastic` block:

```kotlin
fun listProducts(merchantId: UUID): Mono<List<ProductResponse>> =
    Mono.fromCallable {
        enableTenantFilter(merchantId)
        productRepository.findAllByActiveTrue().map { it.toResponse() }
    }.subscribeOn(Schedulers.boundedElastic())
```

#### 3. Cross-tenant Access Guard

`MerchantProductService` calls `accessPolicy.requireProductAccess(actor, merchantId)` before enabling the filter. This produces a `403` before any DB call.

#### 4. Liquibase Changesets

Two new changesets in `20260419-003-merchant-products.sql`:
- `20260419-008-merchant-products`: table DDL
- `20260419-009-merchant-products-rls`: RLS enable + policy

`FORCE ROW LEVEL SECURITY` ensures the application DB user (non-superuser) is also subject to the policy.

### Phase 1 Output (Product Catalog)

→ [data-model.md](data-model.md)
→ [contracts/product-catalog.md](contracts/product-catalog.md)

---

## Merchant Location Feature (added 2026-04-19)

### Summary

Each merchant MAY have an optional physical location stored as a separate `MerchantLocation` entity (table `blitzpay.merchant_locations`). Fields: `latitude DECIMAL(9,6)`, `longitude DECIMAL(9,6)`, `googlePlaceId VARCHAR(255)`. Latitude and longitude are co-required (both-or-neither). Google Place ID is stored as-is with no real-time Maps API validation; future enrichment is deferred to a background job. Managed via `PUT /v1/merchants/{merchantId}/location` (upsert) and `GET /v1/merchants/{merchantId}/location`.

### Technical Context

**Entity**: `MerchantLocation` — 1-to-1 with `MerchantApplication` (UNIQUE FK)
**Precision**: `DECIMAL(9,6)` — ~0.11 m resolution; no PostGIS required
**Validation**: DB CHECK constraints + application-layer validation in `MerchantLocation.update()`
**No multi-tenancy filter needed**: location queries use explicit `merchant_application_id` FK; no cross-tenant risk
**Upsert pattern**: `MerchantLocationRepository.findByMerchantApplicationId()` → create if absent, else update in place

### Constitution Check (Merchant Location)

| Rule | Status |
|------|--------|
| Module ownership — all code stays in `merchant/` | PASS |
| Cross-module communication via events | PASS — no cross-module calls |
| Hibernate `ddl-auto` — schema managed by Liquibase | PASS — one new changeset (table DDL) |
| Spring WebFlux reactive | PASS — blocking JPA wrapped in `boundedElastic` |
| Contract tests required | PASS — `MerchantLocationControllerTest` required |

**Gate result**: PASS.

### Source Code Changes

```text
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── domain/
│   └── MerchantLocation.kt                           CREATE — JPA entity with update() validation
├── application/
│   └── MerchantLocationService.kt                    CREATE — upsert + get logic
├── web/
│   └── MerchantLocationController.kt                 CREATE — PUT/GET /v1/merchants/{id}/location
├── repository/
│   └── MerchantLocationRepository.kt                 CREATE — findByMerchantApplicationId
└── api/
    └── MerchantLocationModels.kt                     CREATE — UpsertLocationRequest, LocationResponse

src/main/resources/db/changelog/
└── 20260419-004-merchant-locations.sql               CREATE — table DDL (changeset 010)

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantLocationControllerTest.kt                 CREATE — PUT 201, PUT 200, PUT 400 (bad coords), GET 200, GET 404

src/test/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantLocationServiceTest.kt                    CREATE — unit tests: create, update, clear coords, validation errors
```

### Implementation Decisions

#### 1. Upsert via findByMerchantApplicationId

```kotlin
@Service
@Transactional
class MerchantLocationService(
    private val locationRepository: MerchantLocationRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository
) {
    fun upsert(merchantId: UUID, request: UpsertLocationRequest): Pair<LocationResponse, Boolean> {
        require(merchantApplicationRepository.existsById(merchantId)) { "Merchant $merchantId not found" }
        val existing = locationRepository.findByMerchantApplicationId(merchantId)
        val (record, created) = if (existing.isPresent) {
            existing.get().also { it.update(request.latitude, request.longitude, request.googlePlaceId) } to false
        } else {
            MerchantLocation(merchantApplicationId = merchantId).also {
                it.update(request.latitude, request.longitude, request.googlePlaceId)
            } to true
        }
        return locationRepository.save(record).toResponse() to created
    }

    fun get(merchantId: UUID): LocationResponse =
        locationRepository.findByMerchantApplicationId(merchantId)
            .orElseThrow { NoSuchElementException("No location for merchant $merchantId") }
            .toResponse()
}
```

#### 2. Controller (reactive wrapper)

```kotlin
@RestController
@RequestMapping("/{version}/merchants/{merchantId}/location")
class MerchantLocationController(private val locationService: MerchantLocationService) {

    @PutMapping
    fun upsert(
        @PathVariable merchantId: UUID,
        @RequestBody request: UpsertLocationRequest
    ): Mono<ResponseEntity<LocationResponse>> =
        Mono.fromCallable { locationService.upsert(merchantId, request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { (response, created) ->
                if (created) ResponseEntity.status(HttpStatus.CREATED).body(response)
                else ResponseEntity.ok(response)
            }

    @GetMapping
    fun get(@PathVariable merchantId: UUID): Mono<ResponseEntity<LocationResponse>> =
        Mono.fromCallable { locationService.get(merchantId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { ResponseEntity.ok(it) }
}
```

#### 3. Request/Response models

```kotlin
data class UpsertLocationRequest(
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val googlePlaceId: String? = null
)

data class LocationResponse(
    val merchantId: UUID,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val googlePlaceId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

#### 4. Liquibase migration

File: `20260419-004-merchant-locations.sql`, changeset `20260419-010-merchant-locations`.
Includes: table DDL with CHECK constraints for lat/lon range and co-requirement (`(latitude IS NULL) = (longitude IS NULL)`).

#### 5. `ContractVerifierBase` additions

Add `@MockitoBean protected lateinit var merchantLocationRepository: MerchantLocationRepository` to `ContractVerifierBase`.

### Phase 1 Output (Merchant Location)

→ [data-model.md](data-model.md)
→ [contracts/merchant-location.md](contracts/merchant-location.md)
