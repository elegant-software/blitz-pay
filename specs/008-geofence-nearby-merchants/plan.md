# Implementation Plan: Geofence-Driven Nearby Merchant Discovery

**Branch**: `008-geofence-nearby-merchants` | **Date**: 2026-04-24 | **Spec**: [spec.md](spec.md)

---

## Summary

Serve geofence region definitions dynamically from the server (replacing the hardcoded prototype list), record proximity events with server-side deduplication, return full merchant + branch context in one proximity response, and enhance the existing nearby-merchants endpoint to include branch data. All new code lives in the existing `merchant` module. One new DB table (`proximity_events`) is added via Liquibase.

---

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux (reactive controllers with `Mono` wrappers), Spring Modulith, Hibernate/JPA, Liquibase  
**Storage**: PostgreSQL 16 (`blitzpay` schema), `ddl-auto: none` — all schema changes via Liquibase  
**Testing**: JUnit 5 + Mockito Kotlin (unit), WebTestClient contract tests (`contract-test` profile)  
**Target Platform**: Linux server (JVM)  
**Project Type**: REST web-service  
**Performance Goals**: Proximity endpoint p95 < 500ms (SC-002); geofence regions endpoint is read-only and cacheable  
**Constraints**: Spring Modulith module boundaries enforced; Liquibase owns all DDL; `ddl-auto: none`  
**Scale/Scope**: Hundreds of merchants, thousands of branches; proximity event volume low (mobile deduplication + 30s cooldown)

---

## Constitution Check

*Checked against `CONSTITUTION.md`*

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin only | ✅ | No Java source files introduced |
| Spring Modulith boundaries | ✅ | All new code within `merchant` module; no cross-module bean coupling |
| Liquibase owns schema | ✅ | New `proximity_events` table via `20260424-003-create-proximity-events.sql` |
| `ddl-auto: none` | ✅ | No Hibernate annotations used to drive DDL |
| Every behavior change needs tests | ✅ | Unit + contract tests planned for all three endpoints |
| No direct cross-module bean coupling | ✅ | `GeofenceService` and `ProximityService` are intra-module |
| Table name prefix matches leaf module | ✅ | Table `proximity_events` — no prefix needed as merchant tables use the `merchant_` prefix convention; see note below |
| `TIMESTAMPTZ` for timestamps | ✅ | `received_at TIMESTAMPTZ` |

> **Table naming note**: Existing merchant tables (`merchant_applications`, `merchant_branches`) use the `merchant_` prefix. The new table follows the same convention: should be `merchant_proximity_events`. The `data-model.md` DDL should use `merchant_proximity_events`. Update the migration accordingly.

---

## Project Structure

### Documentation (this feature)

```text
specs/008-geofence-nearby-merchants/
├── plan.md                          ← this file
├── spec.md                          ← feature specification
├── research.md                      ← Phase 0 decisions
├── data-model.md                    ← entity, migration, response models
├── quickstart.md                    ← source layout and design summary
├── contracts/
│   ├── geofence-regions.json        ← GET /v1/geofence/regions
│   ├── proximity.json               ← POST /v1/proximity
│   └── nearby-merchants-enhanced.json ← GET /v1/merchants/nearby (enhanced)
└── checklists/
    └── requirements.md
```

### Source Code

```text
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── api/
│   ├── GeofenceModels.kt              (new) GeofenceRegionResponse, GeofenceRegionsResponse
│   ├── ProximityModels.kt             (new) ProximityEventRequest, ProximityResponse, MerchantContext, BranchContext
│   └── MerchantLocationModels.kt      (modify) add NearbyBranchResponse; add activeBranches to NearbyMerchantResponse
├── domain/
│   └── ProximityEvent.kt              (new) @Entity → merchant_proximity_events
├── repository/
│   └── ProximityEventRepository.kt    (new) JpaRepository + dedup query
├── application/
│   ├── GeofenceService.kt             (new) buildRegions(lat?, lng?)
│   ├── ProximityService.kt            (new) record(request, userToken?)
│   └── MerchantLocationService.kt     (modify) findNearby → embed activeBranches
└── web/
    ├── GeofenceController.kt          (new) GET /v1/geofence/regions
    └── ProximityController.kt         (new) POST /v1/proximity

src/main/resources/db/changelog/
└── 20260424-003-create-proximity-events.sql   (new)

src/main/resources/application.yml
    (modify) add blitzpay.geofence.proximity-cooldown-seconds: 30

src/test/kotlin/com/elegant/software/blitzpay/merchant/application/
├── GeofenceServiceTest.kt             (new)
└── ProximityServiceTest.kt            (new)

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── GeofenceProximityContractTest.kt   (new)
```

---

## Implementation Phases

### Phase A — Data layer (prereq for everything else)

1. Create `20260424-003-create-proximity-events.sql` (table: `merchant_proximity_events`)
2. Register in `db.changelog-master.yaml`
3. Create `ProximityEvent.kt` entity
4. Create `ProximityEventRepository.kt` with `findLatestWithinWindow` named query
5. Add `blitzpay.geofence.proximity-cooldown-seconds` to `application.yml` and bind via `@ConfigurationProperties`

### Phase B — Geofence regions endpoint (P1, independent)

1. Create `GeofenceModels.kt` (request/response types)
2. Create `GeofenceService.kt` — query `MerchantApplicationRepository.findAll()` and `MerchantBranchRepository.findAllByActiveTrue()`, project to `GeofenceRegionResponse`, optionally sort by Haversine distance
3. Create `GeofenceController.kt` — `GET /v1/geofence/regions`
4. Write `GeofenceServiceTest.kt`
5. Add contract test cases to `GeofenceProximityContractTest.kt`

### Phase C — Proximity event endpoint (P1, depends on Phase A)

1. Create `ProximityModels.kt` (request/response types)
2. Create `ProximityService.kt`:
   - Parse `regionId` to determine source type and UUID
   - Dedup check via `ProximityEventRepository.findLatestWithinWindow`
   - Persist `ProximityEvent`
   - For recorded `enter` events: load merchant and active branches, build `MerchantContext`
   - Set `action = notify` when merchant has active payment channels
3. Create `ProximityController.kt` — `POST /v1/proximity`; extract Bearer token subject when present
4. Write `ProximityServiceTest.kt`
5. Add contract test cases to `GeofenceProximityContractTest.kt`

### Phase D — Nearby merchants enhancement (P2, independent)

1. Add `NearbyBranchResponse` to `MerchantLocationModels.kt`
2. Add `activeBranches` field to `NearbyMerchantResponse`
3. Modify `MerchantLocationService.findNearby`: after fetching nearby merchants, load their active branches in one query (`findAllByMerchantApplicationIdInAndActiveTrue`), group by `merchantApplicationId`, embed into response
4. Update existing `MerchantLocationService` unit tests
5. Update contract test for nearby endpoint shape

---

## Complexity Tracking

No constitution violations. No new modules, no new cross-module dependencies, no pattern deviations.
