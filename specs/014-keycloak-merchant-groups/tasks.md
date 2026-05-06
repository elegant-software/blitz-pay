# Tasks: Keycloak Merchant & Branch Group Sync

**Input**: Design documents from `specs/014-keycloak-merchant-groups/`  
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

**Organization**: Tasks grouped by user story — each story is independently implementable and testable.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Parallelizable (different files, no dependency on incomplete sibling tasks)
- **[US#]**: Maps to user story from spec.md
- Paths are relative to repo root: `src/main/kotlin/com/elegant/software/blitzpay/`

---

## Phase 1: Setup (Module Scaffold)

**Purpose**: Create the `merchant.iam` module skeleton and shared data types needed by all phases.

- [X] T001 Create module declaration in `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/package-info.kt` with `@ApplicationModule(displayName = "Merchant IAM — Keycloak group sync")`
- [X] T002 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/config/KeycloakProperties.kt` — `@ConfigurationProperties(prefix = "keycloak.iam")` data class with `serverUrl`, `realm`, `adminClientId`, `adminClientSecret` fields
- [X] T003 [P] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/outbound/KeycloakGroupModels.kt` — `KeycloakGroupBody(name, attributes)` and `KeycloakGroupRepresentation(id, name, path, attributes)` data classes
- [X] T004 [P] Add `keycloak.iam.*` config stubs to `src/main/resources/application.yml` referencing env vars `KEYCLOAK_IAM_SERVER_URL`, `KEYCLOAK_IAM_REALM`, `KEYCLOAK_IAM_ADMIN_CLIENT_ID`, `KEYCLOAK_IAM_ADMIN_CLIENT_SECRET`
- [X] T005 [P] Update `CLAUDE.md` Required Environment Variables section to document the four new `KEYCLOAK_IAM_*` env vars

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain events, token management, and the `@HttpExchange` Keycloak client — MUST be complete before any user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantIamEvents.kt` — four domain event data classes: `MerchantActivated(merchantId: UUID, merchantName: String)`, `BranchCreated(branchId: UUID, merchantId: UUID, branchName: String, merchantName: String)`, `MerchantNameUpdated(merchantId: UUID, newName: String)`, `BranchNameUpdated(branchId: UUID, merchantId: UUID, newName: String)`
- [X] T007 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/outbound/KeycloakTokenProvider.kt` — `@Component @Profile("!contract-test")` that calls `POST {serverUrl}/realms/master/protocol/openid-connect/token` via a plain `WebClient`, caches the token in `AtomicReference`, exposes `getToken(): Mono<String>` and `invalidate()`
- [X] T008 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/outbound/KeycloakBearerTokenFilter.kt` — `ExchangeFilterFunction` that calls `tokenProvider.getToken()`, injects `Authorization: Bearer <token>`, and on 401 calls `tokenProvider.invalidate()` then retries once
- [X] T009 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/outbound/KeycloakGroupClient.kt` — `@HttpExchange("/groups")` interface with reactive methods: `searchGroups`, `createGroup`, `getChildren`, `createChildGroup`, `updateGroup`
- [X] T010 Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/config/KeycloakWebClientConfig.kt` — `@Configuration @Profile("!contract-test") @ImportHttpServices(group="keycloak-admin", types=[KeycloakGroupClient::class], clientType=WEB_CLIENT)` class with `HttpServiceGroupConfigurer<WebClient.Builder>` bean
- [X] T011 [P] Write `src/test/kotlin/com/elegant/software/blitzpay/merchant/iam/outbound/KeycloakTokenProviderTest.kt` — smoke test (WebClient cannot be easily mocked internally)

**Checkpoint**: Keycloak HTTP interface is wired and injectable. Token provider tested. Domain events declared.

---

## Phase 3: User Story 1 — Merchant Creation Triggers Keycloak Group (Priority: P1) 🎯 MVP

**Goal**: When a merchant is registered directly (status `ACTIVE`), a Keycloak group `/merchants/merchant_<uuid>` is created with `merchant_id` and `merchant_name` attributes. The root `/merchants` group is created if absent.

**Independent Test**: Register a merchant via `POST /v1/merchants/register`; verify Keycloak group `/merchants/merchant_<returned-uuid>` exists with correct attributes. (See `quickstart.md`.)

- [X] T012 [P] [US1] Implement `ensureRootGroup()` and `ensureMerchantGroup()` private methods in `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/service/MerchantGroupSyncService.kt` — idempotent: GET → create if absent, update attributes if present, handle 409 by re-fetching
- [X] T013 [US1] Implement `syncMerchant(event: MerchantActivated): Mono<Void>` in `MerchantGroupSyncService.kt` — chains `ensureRootGroup()` then `ensureMerchantGroup(rootId, event.merchantId, event.merchantName)`
- [X] T014 [US1] Create `src/main/kotlin/com/elegant/software/blitzpay/merchant/iam/listener/MerchantGroupSyncListener.kt` — `@Component @Profile("!contract-test")` with `@ApplicationModuleListener fun on(event: MerchantActivated)` calling `syncService.syncMerchant(event).block()`
- [X] T015 [US1] Wire `MerchantActivated` event publication in `src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationService.kt` — inject `ApplicationEventPublisher`; after `merchantApplicationRepository.save()` when `activateDirectly = true`, publish `MerchantActivated(saved.id, saved.businessProfile.legalBusinessName)`
- [X] T016 [P] [US1] Write `src/test/kotlin/com/elegant/software/blitzpay/merchant/iam/service/MerchantGroupSyncServiceTest.kt` — mock `KeycloakGroupClient`; test `syncMerchant()`: happy path, idempotent update, no-op when missing
- [X] T017 [P] [US1] Extend `src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantRegistrationServiceTest.kt` — mock `ApplicationEventPublisher`; verify `MerchantActivated` published after `register()`, not published after `registerDraft()`

**Checkpoint**: Merchant registration publishes `MerchantActivated`. Sync listener creates Keycloak group. All three test scenarios pass. `./gradlew test --tests "*MerchantRegistrationService*" --tests "*MerchantGroupSyncService*" --tests "*KeycloakTokenProvider*"`

---

## Phase 4: User Story 2 — Branch Creation Triggers Keycloak Sub-Group (Priority: P2)

**Goal**: When a branch is created, a Keycloak sub-group `/merchants/merchant_<merchantId>/branch_<branchId>` is created with `branch_id`, `branch_name`, and `merchant_id` attributes. Parent merchant group is ensured first.

**Independent Test**: With a merchant already registered (US1 complete), create a branch via `POST /v1/merchants/{id}/branches`; verify Keycloak sub-group `/merchants/merchant_<merchantId>/branch_<branchId>` exists with correct attributes.

- [X] T018 [P] [US2] Implement `ensureBranchGroup()` private method in `MerchantGroupSyncService.kt` — idempotent create/update under parent merchant group, handling 409 by re-fetching
- [X] T019 [US2] Implement `syncBranch(event: BranchCreated): Mono<Void>` in `MerchantGroupSyncService.kt` — chains `ensureRootGroup()` → `ensureMerchantGroup(...)` → `ensureBranchGroup(...)`
- [X] T020 [US2] Add `@ApplicationModuleListener fun on(event: BranchCreated)` to `MerchantGroupSyncListener.kt` calling `syncService.syncBranch(event).block()`
- [X] T021 [US2] Wire `BranchCreated` event publication in `src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantBranchService.kt` — inject `ApplicationEventPublisher`; in `create()`, fetch parent merchant and publish `BranchCreated` only when `active = true`
- [X] T022 [P] [US2] Extend `MerchantGroupSyncServiceTest.kt` — test `syncBranch()`: creates branch under existing merchant group, updates when branch already exists
- [X] T023 [P] [US2] Extend `src/test/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantBranchServiceTest.kt` — verify `BranchCreated` published for active branch, not published for inactive branch

**Checkpoint**: Branch creation publishes `BranchCreated`. Sub-group created under correct merchant parent. `./gradlew test --tests "*MerchantBranchService*" --tests "*MerchantGroupSyncService*"`

---

## Phase 5: User Story 3 — Merchant/Branch Name Changes Sync to Keycloak (Priority: P3)

**Goal**: When a merchant's name or a branch's name changes, the corresponding Keycloak group attributes are updated.

**Independent Test**: Update merchant name via `PUT /v1/merchants/{id}`; verify Keycloak attribute `merchant_name` reflects the new value. Same for branch rename.

- [X] T024 [P] [US3] Implement `updateMerchantName(event: MerchantNameUpdated): Mono<Void>` in `MerchantGroupSyncService.kt` — search for `merchant_<merchantId>` by exact name, call `updateGroup()` with new `merchant_name` attribute
- [X] T025 [P] [US3] Implement `updateBranchName(event: BranchNameUpdated): Mono<Void>` in `MerchantGroupSyncService.kt` — search via `findMerchantGroupId()` then `getChildren` for branch, call `updateGroup()` with new `branch_name` attribute
- [X] T026 [US3] Add `@ApplicationModuleListener fun on(event: MerchantNameUpdated)` and `@ApplicationModuleListener fun on(event: BranchNameUpdated)` to `MerchantGroupSyncListener.kt`
- [X] T027 [US3] Wire `MerchantNameUpdated` publication in `src/main/kotlin/com/elegant/software/blitzpay/merchant/service/MerchantManagementService.kt` — inject `ApplicationEventPublisher`; in `update()`, capture `previousName` before `updateProfile`, publish `MerchantNameUpdated` only when name changes
- [X] T028 [US3] Wire `BranchNameUpdated` publication in `MerchantBranchService.kt` — in `update()`, capture `previousName` before `updateDetails`, publish `BranchNameUpdated` only when `saved.name != previousName`
- [X] T029 [P] [US3] Extend `MerchantGroupSyncServiceTest.kt` — test `updateMerchantName()` and `updateBranchName()`: group found → PUT attributes; group not found → no-op (no error)
- [X] T030 [P] [US3] Extend service tests — verify `MerchantNameUpdated` published only when name changes; verify `BranchNameUpdated` published when name changes, not published when name unchanged

**Checkpoint**: All three user stories complete and independently functional. `./gradlew test`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Contract tests, module boundary verification, and observability.

- [X] T031 Create `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/MerchantIamContractTest.kt` — `@MockBean KeycloakGroupClient`; call merchant and branch creation endpoints via `WebTestClient`; capture and assert `MerchantActivated` and `BranchCreated` events are published (use `ApplicationEventPublisher` test spy)
- [X] T032 [P] Verify `ApplicationModules.of(QuickpayApplication::class.java).verify()` still passes — add assertion to the existing module verification test or create `src/test/kotlin/com/elegant/software/blitzpay/merchant/iam/MerchantIamModuleTest.kt`
- [X] T033 [P] Add `@Profile("!contract-test") @Configuration` guard to `KeycloakWebClientConfig.kt` so the `@ImportHttpServices` registration and `HttpServiceGroupConfigurer` bean are excluded from the `contract-test` Spring profile
- [X] T034 Run `./gradlew check` and confirm all unit and contract tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately; T002–T005 all parallelizable
- **Phase 2 (Foundational)**: Depends on Phase 1 completion — **blocks all user stories**
  - T007 before T008 (filter needs provider); T006 before T009/T010 (models needed by interface)
- **Phase 3 (US1)**: Depends on Phase 2 completion
- **Phase 4 (US2)**: Depends on Phase 2 completion; T018–T021 can run after Phase 2 independent of Phase 3
- **Phase 5 (US3)**: Depends on Phase 2 completion; can run after Phase 2 independent of US1/US2
- **Phase 6 (Polish)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Standalone after Phase 2 — no dependency on US2 or US3
- **US2 (P2)**: Standalone after Phase 2 — no dependency on US1 or US3 (though US1 must run first in integration to ensure parent group exists at runtime)
- **US3 (P3)**: Standalone after Phase 2 — update paths are independent of create paths

### Within Each User Story

- Sync service method (T012, T018, T024/T025) before listener handler (T014, T020, T026)
- Listener handler before event publication wiring (events must have a consumer before being published)
- Unit tests can be written in parallel with implementations (different files)

---

## Parallel Opportunities

### Phase 1 (all parallelizable after T001)
```
T001 → then in parallel: T002, T003, T004, T005
```

### Phase 2
```
T006 (events) → then in parallel: T007+T008 (token chain), T009 (interface), T010 (config), T011 (test)
```

### Phase 3 (US1)
```
T012 (private helpers) → T013 (syncMerchant) → T014 (listener) → T015 (wire publisher)
In parallel: T016 (sync service test) and T017 (registration service test)
```

### Phase 4 (US2) — can run in parallel with Phase 3 work on different files
```
T018 (ensureBranchGroup) → T019 (syncBranch) → T020 (listener) → T021 (wire publisher)
In parallel: T022 (sync service test) and T023 (branch service test)
```

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T005)
2. Complete Phase 2: Foundational (T006–T011)
3. Complete Phase 3: US1 — merchant creation (T012–T017)
4. **STOP and VALIDATE**: Follow `quickstart.md` — register a merchant, verify group in Keycloak
5. Ship if merchant group sync alone has sufficient value

### Incremental Delivery

1. Setup + Foundational → Keycloak client wired
2. US1 → Merchant registration creates `/merchants/merchant_<uuid>` — **MVP**
3. US2 → Branch creation creates sub-group
4. US3 → Name changes propagate
5. Each story independently testable and deployable

### Parallel Team Strategy

After Phase 2 is complete:
- Developer A: US1 (T012–T017)
- Developer B: US2 (T018–T023)
- Developer C: US3 (T024–T030)

---

## Notes

- `[P]` tasks operate on different files — safe to run in parallel
- Each `[US#]` phase delivers a complete, independently testable increment
- The Constitution mandates tests for every behaviour change — test tasks are not optional
- Keycloak group names use UUIDs (`merchant_<uuid>`, `branch_<uuid>`); human-readable names live in attributes
- `@ApplicationModuleListener` + Modulith Event Publication Registry provides at-least-once delivery — listener must be idempotent
- Commit after each task or logical group; run `./gradlew check` before pushing
