# Implementation Plan: Keycloak Merchant & Branch Group Sync

**Branch**: `014-keycloak-merchant-groups` | **Date**: 2026-05-06 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/014-keycloak-merchant-groups/spec.md`

## Summary

Automatically maintain a Keycloak group hierarchy (`/merchants/merchant_<uuid>/branch_<uuid>`) whenever a merchant or branch is created or renamed in BlitzPay. A new `merchant.iam` Spring Modulith sub-module listens to domain events from the `merchant` module and synchronises them to Keycloak.

The Keycloak Admin REST API client uses **Spring Boot 4's declarative HTTP Interface** feature: `KeycloakGroupClient` is defined as a `@HttpExchange`-annotated interface, registered with `@ImportHttpServices`, and auto-proxied by Spring — no hand-rolled WebClient exchange calls, no SDK.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith 2.1.0-M3, Spring Framework 7 HTTP Interfaces (`@HttpExchange`, `@ImportHttpServices`)  
**Storage**: No new tables; existing `event_publication` Modulith table for at-least-once delivery  
**Testing**: JUnit 5 + Mockito Kotlin (unit); WebTestClient (contract)  
**Target Platform**: Linux server (k8s)  
**Project Type**: Reactive web service (Spring Boot WebFlux)  
**Performance Goals**: Keycloak group created within 5 s of trigger under normal conditions  
**Constraints**: Non-blocking merchant/branch creation; async IAM sync with at-least-once delivery  
**Scale/Scope**: One Keycloak group per merchant + one per branch; hundreds to low thousands

## Constitution Check

| Rule | Status | Note |
|------|--------|------|
| Kotlin-only source files | PASS | All new files are `.kt` |
| Spring Modulith module boundaries | PASS | `merchant.iam` is a named sub-module; consumes events from `merchant.api` named interface |
| Cross-module via events only | PASS | `merchant.iam` receives `ApplicationEventPublisher` events; no direct bean injection from `merchant` |
| No new HTTP client when WebClient available | PASS | `@ImportHttpServices` proxies are backed by the existing `WebClient` stack — no new HTTP client lib |
| Liquibase owns schema; no new tables | PASS | Event publication table already exists; no migration needed |
| Logging via SLF4J `LoggerFactory` | PASS | No KotlinLogging |
| Tests required for all behaviour changes | PASS | Unit + contract tests planned |
| Table naming prefix convention | PASS | No new tables in scope |

## Project Structure

### Documentation (this feature)

```text
specs/014-keycloak-merchant-groups/
├── plan.md              # This file
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── domain-events.md
│   └── keycloak-admin-api.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
│
├── merchant/
│   ├── api/
│   │   ├── MerchantIamEvents.kt              # NEW — 4 domain event data classes
│   │   └── package-info.kt                   # existing @NamedInterface
│   │
│   ├── application/
│   │   ├── MerchantRegistrationService.kt    # MODIFY — publish MerchantActivated
│   │   ├── MerchantBranchService.kt          # MODIFY — publish BranchCreated, BranchNameUpdated
│   │   └── MerchantManagementService.kt      # MODIFY — publish MerchantNameUpdated
│   │
│   └── iam/
│       ├── package-info.kt                   # NEW — @ApplicationModule
│       └── internal/
│           ├── KeycloakProperties.kt         # NEW — @ConfigurationProperties(prefix="keycloak.iam")
│           ├── KeycloakTokenProvider.kt      # NEW — client-credentials token cache
│           ├── KeycloakBearerTokenFilter.kt  # NEW — ExchangeFilterFunction (inject + refresh token)
│           ├── KeycloakGroupClient.kt        # NEW — @HttpExchange interface (no impl class)
│           ├── KeycloakWebClientConfig.kt    # NEW — @ImportHttpServices + HttpServiceGroupConfigurer
│           ├── KeycloakGroupModels.kt        # NEW — KeycloakGroupBody, KeycloakGroupRepresentation
│           ├── MerchantGroupSyncService.kt   # NEW — idempotent orchestration (uses GroupClient)
│           └── MerchantGroupSyncListener.kt  # NEW — @ApplicationModuleListener handlers

src/main/resources/
└── application.yml                           # MODIFY — add keycloak.iam.* config stubs

src/test/kotlin/com/elegant/software/blitzpay/merchant/
├── iam/
│   ├── MerchantGroupSyncServiceTest.kt       # NEW — mock KeycloakGroupClient interface
│   └── KeycloakTokenProviderTest.kt          # NEW — mock WebClient, verify token fetch/cache
├── application/
│   ├── MerchantRegistrationServiceTest.kt    # EXTEND — verify MerchantActivated published
│   └── MerchantBranchServiceTest.kt          # EXTEND — verify BranchCreated/BranchNameUpdated published

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── MerchantIamContractTest.kt                # NEW — mock KeycloakGroupClient bean
```

**Structure Decision**: Single-project layout. New code under `merchant.iam` sub-module following the `payments.push` pattern.

## Implementation Steps

### Step 1 — Domain events in `merchant.api`

Create `MerchantIamEvents.kt` with four Kotlin `data class` event types (all in `com.elegant.software.blitzpay.merchant.api`):

```kotlin
data class MerchantActivated(val merchantId: UUID, val merchantName: String)
data class BranchCreated(val branchId: UUID, val merchantId: UUID, val branchName: String, val merchantName: String)
data class MerchantNameUpdated(val merchantId: UUID, val newName: String)
data class BranchNameUpdated(val branchId: UUID, val merchantId: UUID, val newName: String)
```

### Step 2 — Wire event publication in existing services

**`MerchantRegistrationService`**: inject `ApplicationEventPublisher`; in `saveRegistration()`, after `save()`, if `activateDirectly`:
```kotlin
publisher.publishEvent(MerchantActivated(saved.id, saved.businessProfile.legalBusinessName))
```

**`MerchantBranchService`**: inject `ApplicationEventPublisher`; in `create()`, after `save()`:
```kotlin
// Fetch merchant name from parent for the event
publisher.publishEvent(BranchCreated(branch.id, branch.merchantApplicationId, branch.name, merchantName))
```
In `update()`, if `request.name != branch.name` (before save):
```kotlin
publisher.publishEvent(BranchNameUpdated(branch.id, branch.merchantApplicationId, request.name))
```

**`MerchantManagementService`**: inject `ApplicationEventPublisher`; in `updateProfile()`, if `legalBusinessName` changed:
```kotlin
publisher.publishEvent(MerchantNameUpdated(application.id, request.legalBusinessName))
```

### Step 3 — `merchant.iam` module scaffold

`package-info.kt`:
```kotlin
@ApplicationModule(displayName = "Merchant IAM — Keycloak group sync")
package com.elegant.software.blitzpay.merchant.iam
```

`KeycloakProperties.kt`:
```kotlin
@ConfigurationProperties(prefix = "keycloak.iam")
data class KeycloakProperties(
    val serverUrl: String,
    val realm: String,
    val adminClientId: String,
    val adminClientSecret: String,
)
```

### Step 4 — Token management (`KeycloakTokenProvider` + `KeycloakBearerTokenFilter`)

`KeycloakTokenProvider` — a `@Component` that:
- Calls `POST ${serverUrl}/realms/master/protocol/openid-connect/token` using a plain `WebClient` (separate from the group client)
- Caches the token in an `AtomicReference<Pair<String, Instant>>`; refreshes before expiry
- Exposes `getToken(): Mono<String>` and `invalidate()`

`KeycloakBearerTokenFilter` — an `ExchangeFilterFunction` that injects `Authorization: Bearer <token>` and retries once on 401.

### Step 5 — `KeycloakGroupClient` (HTTP Interface)

```kotlin
@HttpExchange("/groups")
interface KeycloakGroupClient {

    @GetExchange
    fun searchGroups(
        @RequestParam search: String,
        @RequestParam exact: Boolean = true,
    ): Mono<List<KeycloakGroupRepresentation>>

    @PostExchange
    fun createGroup(@RequestBody body: KeycloakGroupBody): Mono<ResponseEntity<Void>>

    @GetExchange("/{parentId}/children")
    fun getChildren(
        @PathVariable parentId: String,
        @RequestParam search: String,
        @RequestParam exact: Boolean = true,
    ): Mono<List<KeycloakGroupRepresentation>>

    @PostExchange("/{parentId}/children")
    fun createChildGroup(
        @PathVariable parentId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<ResponseEntity<Void>>

    @PutExchange("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<Void>
}
```

No implementation class. Spring Boot 4 creates the proxy automatically.

### Step 6 — `KeycloakWebClientConfig` (`@ImportHttpServices`)

```kotlin
@Configuration
@ImportHttpServices(
    group = "keycloak-admin",
    types = [KeycloakGroupClient::class],
    clientType = HttpServiceGroup.ClientType.WEB_CLIENT,
)
class KeycloakWebClientConfig {

    @Bean
    fun keycloakGroupConfigurer(
        properties: KeycloakProperties,
        tokenProvider: KeycloakTokenProvider,
    ): HttpServiceGroupConfigurer = HttpServiceGroupConfigurer { groups ->
        groups.filterByName("keycloak-admin")
              .forEachClient { _, builder ->
                  builder.baseUrl("${properties.serverUrl}/admin/realms/${properties.realm}")
                  builder.filter(KeycloakBearerTokenFilter(tokenProvider))
              }
    }
}
```

Spring Boot 4 auto-configuration picks up `HttpServiceGroupConfigurer` beans automatically — no manual `HttpServiceProxyFactory` wiring needed.

### Step 7 — `MerchantGroupSyncService`

Idempotent orchestration using `KeycloakGroupClient` (injected as a regular Spring bean):

```kotlin
@Service
class MerchantGroupSyncService(private val client: KeycloakGroupClient) {

    fun syncMerchant(event: MerchantActivated): Mono<Void> =
        ensureRootGroup()
            .flatMap { rootId -> ensureMerchantGroup(rootId, event.merchantId, event.merchantName) }
            .then()

    fun syncBranch(event: BranchCreated): Mono<Void> =
        ensureRootGroup()
            .flatMap { rootId -> ensureMerchantGroup(rootId, event.merchantId, event.merchantName) }
            .flatMap { merchantGroupId -> ensureBranchGroup(merchantGroupId, event.branchId, event.branchName, event.merchantId) }
            .then()

    // ... updateMerchantName, updateBranchName
}
```

Idempotency: always GET first; if found → PUT attributes; if absent → POST; on 409 → GET again.  
Location header from 201 responses carries the new group ID.

### Step 8 — `MerchantGroupSyncListener`

```kotlin
@Component
class MerchantGroupSyncListener(private val syncService: MerchantGroupSyncService) {

    @ApplicationModuleListener
    fun on(event: MerchantActivated) = syncService.syncMerchant(event).block()

    @ApplicationModuleListener
    fun on(event: BranchCreated) = syncService.syncBranch(event).block()

    @ApplicationModuleListener
    fun on(event: MerchantNameUpdated) = syncService.updateMerchantName(event).block()

    @ApplicationModuleListener
    fun on(event: BranchNameUpdated) = syncService.updateBranchName(event).block()
}
```

Spring Modulith's Event Publication Registry wraps these — failures are retried on restart via the existing `event_publication` table.

### Step 9 — Config and contract-test mock

`application.yml`:
```yaml
keycloak:
  iam:
    server-url: ${KEYCLOAK_IAM_SERVER_URL}
    realm: ${KEYCLOAK_IAM_REALM}
    admin-client-id: ${KEYCLOAK_IAM_ADMIN_CLIENT_ID}
    admin-client-secret: ${KEYCLOAK_IAM_ADMIN_CLIENT_SECRET}
```

Under the `contract-test` Spring profile: `@MockBean KeycloakGroupClient` so contract tests need no live Keycloak. Ensure `KeycloakWebClientConfig` is excluded from `contract-test` profile (alongside existing TrueLayer mocks).

### Step 10 — Tests

**Unit tests**:
- `MerchantGroupSyncServiceTest` — mock `KeycloakGroupClient` via Mockito Kotlin; test all four sync operations including idempotency (409 path, GET fallback)
- `KeycloakTokenProviderTest` — mock WebClient; verify token fetch, caching, and invalidation on 401
- Extend `MerchantRegistrationServiceTest` — verify `MerchantActivated` published after `register()`
- Extend `MerchantBranchServiceTest` — verify `BranchCreated` and `BranchNameUpdated` published

**Contract tests**:
- `MerchantIamContractTest` — `@MockBean KeycloakGroupClient`; call merchant/branch creation endpoints via `WebTestClient`; verify events are published (capture with `ApplicationEventPublisher` spy)

**Module verification**:
```bash
./gradlew test --tests "*.ApplicationModulesTest"   # must still pass
./gradlew check                                      # full unit + contract suite
```

## Complexity Tracking

*No Constitution violations.*
