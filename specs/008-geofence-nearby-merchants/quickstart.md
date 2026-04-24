# Quickstart: Geofence-Driven Nearby Merchant Discovery

**Branch**: `008-geofence-nearby-merchants`

---

## What's being built

Three server-side capabilities:

1. **`GET /v1/geofence/regions`** — serves the geofence region list to the mobile, replacing the hardcoded `MERCHANT_REGIONS` in the prototype. Regions are derived from existing `merchant_applications` and `merchant_branches` location data.

2. **`POST /v1/proximity`** — records a geofence enter/exit event, deduplicates within a 30-second window, and returns full merchant + branch context in a single response.

3. **`GET /v1/merchants/nearby` (enhanced)** — additive change; adds `activeBranches` list to each merchant result.

---

## Source layout (new files only)

```
src/main/kotlin/com/elegant/software/blitzpay/merchant/
├── api/
│   ├── GeofenceModels.kt          # GeofenceRegionResponse, GeofenceRegionsResponse
│   ├── ProximityModels.kt         # ProximityEventRequest, ProximityResponse, MerchantContext, BranchContext
│   └── MerchantLocationModels.kt  # (existing — add NearbyBranchResponse, update NearbyMerchantResponse)
├── domain/
│   └── ProximityEvent.kt          # @Entity for proximity_events table
├── repository/
│   └── ProximityEventRepository.kt  # findLatestWithinWindow(...)
├── application/
│   ├── GeofenceService.kt         # buildRegions(lat?, lng?) → GeofenceRegionsResponse
│   └── ProximityService.kt        # record(request, userToken?) → ProximityResponse
└── web/
    ├── GeofenceController.kt      # GET /v1/geofence/regions
    └── ProximityController.kt     # POST /v1/proximity

src/main/resources/db/changelog/
└── 20260424-003-create-proximity-events.sql

src/main/resources/application.yml
    # add: blitzpay.geofence.proximity-cooldown-seconds: 30

src/test/kotlin/com/elegant/software/blitzpay/merchant/application/
├── GeofenceServiceTest.kt
└── ProximityServiceTest.kt

src/contractTest/kotlin/com/elegant/software/blitzpay/merchant/
└── GeofenceProximityContractTest.kt
```

---

## Key design decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Region ID format | `merchant:{uuid}` / `branch:{uuid}` | Self-describing; server can parse source type and ID from the region string alone |
| Geofence region source | Query-time projection from existing tables | No duplicate data; branches and merchants already carry lat/lng/radius |
| Deduplication mechanism | DB query on `proximity_events` within cooldown window | No Redis dependency; event volume is low |
| Module placement | Within existing `merchant` module | Geofencing is merchant data; no cross-module coupling needed |
| Nearby enhancement | Additive field `activeBranches` | Backward-compatible; existing mobile clients unaffected |
| Per-user geofence state | Not tracked server-side | Exit events are unreliable on mobile; events log is sufficient |

---

## Configuration

```yaml
blitzpay:
  geofence:
    proximity-cooldown-seconds: 30   # deduplication window; mirrors prototype client-side cooldown
```

---

## Liquibase migration

Add `20260424-003-create-proximity-events.sql` and register in `db.changelog-master.yaml`. See `data-model.md` for the full DDL.

---

## Testing strategy

- **Unit tests**: `GeofenceServiceTest` — verify region projection logic (merchant vs branch source, distance sorting, inactive exclusion). `ProximityServiceTest` — verify deduplication (within/outside window), `action` flag logic, anonymous vs authenticated paths.
- **Contract tests**: `GeofenceProximityContractTest` — `GET /v1/geofence/regions` returns correct shape; `POST /v1/proximity` returns correct shape for enter/exit/dedup cases; `GET /v1/merchants/nearby` includes `activeBranches`.
- **No integration test with real DB required** for v1 — deduplication logic is covered by unit tests with a mocked repository.
