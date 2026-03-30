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
