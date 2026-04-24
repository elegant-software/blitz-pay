# Tasks: Geofence-Driven Nearby Merchant Discovery

**Input**: Design documents from `specs/008-geofence-nearby-merchants/`  
**Branch**: `008-geofence-nearby-merchants`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

---

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on sibling tasks)
- **[US1]**: User Story 1 — Mobile fetches geofence regions from server
- **[US2]**: User Story 2 — Server records proximity event and returns merchant context
- **[US3]**: User Story 3 — Nearby merchants endpoint includes branch-level context

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration and `@ConfigurationProperties` binding for geofence settings

- [ ] T001 Add `blitzpay.geofence.proximity-cooldown-seconds: 30` to `src/main/resources/application.yml`
- [ ] T002 Create `GeofenceProperties.kt` (`@ConfigurationProperties(prefix = "blitzpay.geofence")`) in `src/main/kotlin/com/elegant/software/blitzpay/merchant/config/GeofenceProperties.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: DB migration and `ProximityEvent` persistence layer — required by US2; US1 and US3 can proceed in parallel once Phase 1 is done

**⚠️ CRITICAL**: US2 cannot begin until T003–T006 are complete

- [ ] T003 Create Liquibase migration `src/main/resources/db/changelog/20260424-003-create-proximity-events.sql` — table `blitzpay.merchant_proximity_events` with columns `id`, `region_id`, `region_type`, `source_id`, `user_token`, `device_id`, `event_type`, `reported_latitude`, `reported_longitude`, `received_at TIMESTAMPTZ` and indexes `ix_merchant_proximity_events_region_event (region_id, event_type, received_at DESC)` and `ix_merchant_proximity_events_user_region (user_token, region_id, received_at DESC)`, with rollback directive
- [ ] T004 Register the new migration in `src/main/resources/db/changelog/db.changelog-master.yaml` (append after `20260424-002-branch-merchant-fk-cascade.sql`)
- [ ] T005 [P] Create `ProximityEvent.kt` `@Entity` mapping to `blitzpay.merchant_proximity_events` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/domain/ProximityEvent.kt` — fields: `id: UUID`, `regionId: String`, `regionType: String`, `sourceId: UUID`, `userToken: String?`, `deviceId: String?`, `eventType: String`, `reportedLatitude: Double`, `reportedLongitude: Double`, `receivedAt: Instant`
- [ ] T006 [P] Create `ProximityEventRepository.kt` extending `JpaRepository<ProximityEvent, UUID>` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/repository/ProximityEventRepository.kt` — add method `existsByRegionIdAndEventTypeAndUserTokenAndReceivedAtAfter(regionId: String, eventType: String, userToken: String, since: Instant): Boolean` and equivalent for `deviceId`

**Checkpoint**: Data layer ready — US1 and US3 can now proceed fully; US2 can now proceed

---

## Phase 3: User Story 1 — Mobile Fetches Geofence Regions (Priority: P1) 🎯 MVP

**Goal**: Replace hardcoded `MERCHANT_REGIONS` in the prototype — mobile calls `GET /v1/geofence/regions` and registers OS geofences dynamically.

**Independent Test**: Call `GET /v1/geofence/regions` with and without `lat`/`lng` params; verify response contains active merchant and branch regions; verify inactive merchants/branches are excluded; verify ordering when coords supplied.

- [ ] T007 [P] [US1] Create `GeofenceModels.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/GeofenceModels.kt` — data classes `GeofenceRegionResponse(regionId, regionType, sourceId, displayName, latitude, longitude, radiusMeters, distanceMeters?)` and `GeofenceRegionsResponse(regions: List<GeofenceRegionResponse>)`
- [ ] T008 [US1] Create `GeofenceService.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/GeofenceService.kt` — `buildRegions(lat: Double?, lng: Double?): GeofenceRegionsResponse`; query `MerchantApplicationRepository` for merchants with non-null location and `status == ACTIVE`; query `MerchantBranchRepository.findAllByActiveTrue()` for branches with non-null location; project each to `GeofenceRegionResponse` using `regionId = "merchant:{id}"` or `"branch:{id}"`; if `lat`/`lng` supplied, sort by Haversine distance ascending using the existing `haversineMeters` utility
- [ ] T009 [US1] Create `GeofenceController.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/web/GeofenceController.kt` — `@RestController @RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/geofence", version = "1")`; `@GetMapping("/regions") fun regions(@RequestParam lat: Double?, @RequestParam lng: Double?): Mono<ResponseEntity<GeofenceRegionsResponse>>`
- [ ] T010 [US1] Write `GeofenceServiceTest.kt` in `src/test/kotlin/com/elegant/software/blitzpay/merchant/application/GeofenceServiceTest.kt` — unit tests: returns merchant region, returns branch region, excludes inactive merchant, excludes inactive branch, excludes merchant/branch without location, sorts by distance when coords provided, returns empty list when no active located entities
- [ ] T011 [US1] Create `GeofenceProximityContractTest.kt` in `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/GeofenceProximityContractTest.kt` — contract tests for `GET /v1/geofence/regions`: `200` with region list shape; `200` with empty list when no merchants have location; query params `lat`/`lng` are optional

**Checkpoint**: US1 fully functional and independently testable — `GET /v1/geofence/regions` live

---

## Phase 4: User Story 2 — Proximity Event Recording (Priority: P1)

**Goal**: Mobile posts geofence entry/exit; server deduplicates, persists, and returns full merchant + branch context in one response.

**Independent Test**: `POST /v1/proximity` with a valid `enter` event; verify `recorded: true`, `action: notify` (when merchant has active payment channels), and `merchant` object present with branches. Post same event again within 30s; verify `recorded: false`.

- [ ] T012 [P] [US2] Create `ProximityModels.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/ProximityModels.kt` — data classes: `ProximityLocation(latitude: Double, longitude: Double)`, `ProximityEventRequest(regionId: String, event: String, location: ProximityLocation, timestamp: String, deviceId: String?)`, `BranchContext(branchId, name, distanceMeters?, addressLine1?, addressLine2?, city?, postalCode?, country?, contactFullName?, contactEmail?, contactPhoneNumber?, activePaymentChannels: Set<MerchantPaymentChannel>)`, `MerchantContext(merchantId, name, logoUrl?, activePaymentChannels: Set<MerchantPaymentChannel>, branches: List<BranchContext>)`, `ProximityResponse(recorded: Boolean, action: String, merchant: MerchantContext?)`
- [ ] T013 [US2] Create `ProximityService.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/ProximityService.kt` — inject `ProximityEventRepository`, `MerchantApplicationRepository`, `MerchantBranchRepository`, `StorageService`, `GeofenceProperties`; method `record(request: ProximityEventRequest, userToken: String?): ProximityResponse`; parse `regionId` prefix (`merchant:` vs `branch:`) to determine `sourceId`; dedup check via `ProximityEventRepository`; persist `ProximityEvent`; for recorded `enter` events resolve merchant (from `sourceId` directly or via branch's `merchantApplicationId`), build `MerchantContext` with active branches sorted by distance from `request.location`; set `action = "notify"` when `merchant.activePaymentChannels.isNotEmpty()`
- [ ] T014 [US2] Create `ProximityController.kt` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/web/ProximityController.kt` — `@RestController @RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/proximity", version = "1")`; `@PostMapping fun record(@RequestBody request: ProximityEventRequest, @RequestHeader(required = false) authorization: String?): Mono<ResponseEntity<ProximityResponse>>`; extract bearer token subject from `Authorization` header when present and pass as `userToken`
- [ ] T015 [US2] Write `ProximityServiceTest.kt` in `src/test/kotlin/com/elegant/software/blitzpay/merchant/application/ProximityServiceTest.kt` — unit tests: records enter event for merchant region; records enter event for branch region; deduplicates within cooldown window (mocked repo returns existing record); does not deduplicate after cooldown expires; returns `action=notify` when merchant has active payment channels; returns `action=none` for exit event; returns `recorded=false` for unknown regionId; anonymous path (no userToken, deviceId used); merchant context includes active branches sorted by distance
- [ ] T016 [US2] Add proximity contract test cases to `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/GeofenceProximityContractTest.kt` — `POST /v1/proximity`: `200` recorded enter response shape with merchant context; `200` deduplicated response (`recorded: false`, no merchant); `200` exit event (`action: none`); `422` invalid request (missing required fields)

**Checkpoint**: US2 fully functional — `POST /v1/proximity` live with deduplication and merchant context

---

## Phase 5: User Story 3 — Nearby Merchants With Branch Context (Priority: P2)

**Goal**: `GET /v1/merchants/nearby` response includes `activeBranches` per merchant — additive, backward-compatible.

**Independent Test**: Call `GET /v1/merchants/nearby` with coords and radius; verify each merchant result has an `activeBranches` list; verify inactive branches excluded; verify branch with no location appears in list with `distanceMeters` omitted.

- [ ] T017 [P] [US3] Add `NearbyBranchResponse(branchId, name, distanceMeters?, latitude?, longitude?, addressLine1?, city?, postalCode?, country?, contactFullName?, contactEmail?, contactPhoneNumber?, activePaymentChannels: Set<MerchantPaymentChannel>, imageUrl?)` to `src/main/kotlin/com/elegant/software/blitzpay/merchant/api/MerchantLocationModels.kt` and add `activeBranches: List<NearbyBranchResponse> = emptyList()` field to `NearbyMerchantResponse`
- [ ] T018 [P] [US3] Add `findAllByMerchantApplicationIdInAndActiveTrue(merchantIds: Collection<UUID>): List<MerchantBranch>` to `src/main/kotlin/com/elegant/software/blitzpay/merchant/repository/MerchantBranchRepository.kt`
- [ ] T019 [US3] Modify `MerchantLocationService.findNearby` in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantLocationService.kt` — after fetching nearby merchants, call `merchantBranchRepository.findAllByMerchantApplicationIdInAndActiveTrue(merchantIds)` in one query; group by `merchantApplicationId`; map each branch to `NearbyBranchResponse` (compute `distanceMeters` via `haversineMeters` when branch location is non-null; presign `imageStorageKey`); embed sorted branch list (by `distanceMeters` ascending, branches without location last)
- [ ] T020 [US3] Update `MerchantBranchService` injection in `MerchantLocationService` constructor — add `MerchantBranchRepository` dependency; update constructor and Spring wiring in `src/main/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantLocationService.kt`
- [ ] T021 [US3] Update existing nearby tests in `src/test/kotlin/com/elegant/software/blitzpay/merchant/application/MerchantBranchServiceTest.kt` (or create `MerchantLocationServiceTest.kt`) to verify `activeBranches` is populated correctly and inactive branches are excluded
- [ ] T022 [US3] Add nearby-enhanced contract test cases to `src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/GeofenceProximityContractTest.kt` — `GET /v1/merchants/nearby`: response includes `activeBranches` array per merchant; empty array when merchant has no active located branches

**Checkpoint**: US3 fully functional — `GET /v1/merchants/nearby` includes branch data

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T023 [P] Verify Spring Modulith module structure passes `ApplicationModules.of(QuickpayApplication::class.java).verify()` — run `./gradlew test --tests "*ModularityTests*"` or equivalent verify test in `src/test/kotlin/`
- [ ] T024 Run `./gradlew check` (unit + contract tests) and confirm all pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS US2 only**
- **Phase 3 (US1)**: Depends on Phase 1 only — can start as soon as T001/T002 done
- **Phase 4 (US2)**: Depends on Phase 2 (needs `ProximityEventRepository`) — start after T003–T006
- **Phase 5 (US3)**: Depends on Phase 1 only — can start as soon as T001 done (no DB changes needed)
- **Phase 6 (Polish)**: Depends on all desired stories being complete

### User Story Dependencies

- **US1**: Independent after Phase 1 — no dependency on US2 or US3
- **US2**: Depends on Phase 2 (Foundational) — independent of US1 and US3
- **US3**: Independent after Phase 1 — no dependency on US1 or US2

### Within Each Phase

- T007 (models) before T008 (service) before T009 (controller) — within US1
- T012 (models) before T013 (service) before T014 (controller) — within US2
- T017/T018 (model + repo) before T019/T020 (service modification) — within US3

---

## Parallel Opportunities

### Phase 2 (Foundational) — after T003/T004 complete:
```
T005: ProximityEvent.kt entity
T006: ProximityEventRepository.kt
```

### Phase 3 (US1) — after Phase 1:
```
T007: GeofenceModels.kt   (no dependencies)
T010: GeofenceServiceTest.kt   (write alongside T008)
```

### Phase 4 (US2) — after Phase 2:
```
T012: ProximityModels.kt   (no dependencies)
```

### Phase 5 (US3) — after Phase 1:
```
T017: NearbyBranchResponse model update
T018: MerchantBranchRepository new method
```

### Cross-story parallel (after Phase 1):
```
Developer A: US1 (T007 → T008 → T009 → T010 → T011)
Developer B: Phase 2 then US2 (T003 → T004 → T005/T006 → T012 → T013 → T014 → T015 → T016)
Developer C: US3 (T017/T018 → T019 → T020 → T021 → T022)
```

---

## Implementation Strategy

### MVP (US1 + US2 — both P1)

1. Complete Phase 1 (T001–T002)
2. Complete Phase 2 (T003–T006) — in parallel with US1 where possible
3. Complete Phase 3 / US1 (T007–T011) — `GET /v1/geofence/regions` live
4. Complete Phase 4 / US2 (T012–T016) — `POST /v1/proximity` live
5. **STOP and VALIDATE**: Both P1 stories independently testable

### Full Delivery

6. Complete Phase 5 / US3 (T017–T022) — enhanced nearby endpoint
7. Complete Phase 6 (T023–T024) — module verification + full test suite

---

## Notes

- `merchant_proximity_events` table name (not `proximity_events`) — follows `merchant_` prefix convention per `CONSTITUTION.md` and `reference/liquibase-best-practices.md`
- `regionId` format `merchant:{uuid}` / `branch:{uuid}` is the stable contract between mobile and server — do not change format once shipped
- All new controllers use the same versioning pattern as existing controllers: `@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/...", version = "1")`
- `GeofenceProximityContractTest.kt` covers all three endpoints in one file; the `contract-test` profile mocks TrueLayer but leaves JPA mocked — add `@MockitoBean TrueLayerClient` as done in `MerchantApplicationRepositoryTest`
- Haversine distance utility already exists in `MerchantLocationService` — extract to a shared private function or duplicate inline (do not create a shared utility class across modules)
