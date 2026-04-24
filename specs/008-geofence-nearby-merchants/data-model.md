# Data Model: Geofence-Driven Nearby Merchant Discovery

**Branch**: `008-geofence-nearby-merchants` | **Phase**: 1 | **Date**: 2026-04-24

---

## New Entity: ProximityEvent

**Table**: `blitzpay.proximity_events`  
**Module**: `merchant`  
**Owned by**: `merchant` module exclusively

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | `UUID` | NO | PK |
| `region_id` | `VARCHAR(255)` | NO | `merchant:{uuid}` or `branch:{uuid}` |
| `region_type` | `VARCHAR(16)` | NO | `MERCHANT` or `BRANCH` |
| `source_id` | `UUID` | NO | `merchantApplicationId` or `merchantBranchId` |
| `user_token` | `VARCHAR(512)` | YES | Authenticated user identifier (bearer subject); null when anonymous |
| `device_id` | `VARCHAR(255)` | YES | Stable anonymous device identifier; null when authenticated |
| `event_type` | `VARCHAR(8)` | NO | `ENTER` or `EXIT` |
| `reported_latitude` | `DOUBLE PRECISION` | NO | Coordinates reported by mobile |
| `reported_longitude` | `DOUBLE PRECISION` | NO | |
| `received_at` | `TIMESTAMPTZ` | NO | Server wall-clock time of receipt |

**Indexes**:
- `ix_proximity_events_region_event` on `(region_id, event_type, received_at DESC)` — supports deduplication query
- `ix_proximity_events_user_region` on `(user_token, region_id, received_at DESC)` — supports per-user queries (nullable column; partial index may be appropriate in future)

**Deduplication query pattern**:
```sql
SELECT received_at FROM blitzpay.proximity_events
WHERE region_id = :regionId
  AND event_type = :eventType
  AND (user_token = :userToken OR device_id = :deviceId)
  AND received_at > NOW() - INTERVAL ':cooldownSeconds seconds'
ORDER BY received_at DESC
LIMIT 1
```

---

## Existing Entities (unchanged schema, enhanced queries)

### MerchantApplication (existing)
- `location` (embedded `MerchantLocation`) — source of merchant-level geofence regions
- `region_id` projected as `merchant:{id}`
- No schema change

### MerchantBranch (existing)
- `location` (embedded `MerchantLocation`) — source of branch-level geofence regions
- `region_id` projected as `branch:{id}`
- No schema change

---

## Liquibase Migration

**File**: `20260424-003-create-proximity-events.sql`

```sql
-- liquibase formatted sql

-- changeset mehdi:20260424-003-create-proximity-events
CREATE TABLE blitzpay.proximity_events (
    id                  UUID            NOT NULL,
    region_id           VARCHAR(255)    NOT NULL,
    region_type         VARCHAR(16)     NOT NULL,
    source_id           UUID            NOT NULL,
    user_token          VARCHAR(512),
    device_id           VARCHAR(255),
    event_type          VARCHAR(8)      NOT NULL,
    reported_latitude   DOUBLE PRECISION NOT NULL,
    reported_longitude  DOUBLE PRECISION NOT NULL,
    received_at         TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_proximity_events PRIMARY KEY (id)
);
CREATE INDEX ix_proximity_events_region_event
    ON blitzpay.proximity_events (region_id, event_type, received_at DESC);
CREATE INDEX ix_proximity_events_user_region
    ON blitzpay.proximity_events (user_token, region_id, received_at DESC);
-- rollback DROP TABLE blitzpay.proximity_events;
```

---

## Response Model Additions (no DB change)

### GeofenceRegionResponse (new — query-time projection)
```
regionId          : String        — "merchant:{uuid}" or "branch:{uuid}"
regionType        : String        — "MERCHANT" | "BRANCH"
sourceId          : UUID          — merchantApplicationId or merchantBranchId
displayName       : String        — legalBusinessName or branch name
latitude          : Double
longitude         : Double
radiusMeters      : Int
distanceMeters    : Double?       — present when caller position provided
```

### ProximityEventRequest (new)
```
regionId          : String        — matches a GeofenceRegionResponse.regionId
event             : String        — "enter" | "exit"
location.latitude : Double
location.longitude: Double
timestamp         : String        — ISO-8601 (client wall-clock; informational only)
deviceId          : String?       — stable anonymous device ID
```

### ProximityResponse (new)
```
recorded          : Boolean
action            : String        — "notify" | "none"
merchant          : MerchantContext?   — present when recorded=true and event=enter
```

### MerchantContext (new — embedded in ProximityResponse)
```
merchantId              : UUID
name                    : String
logoUrl                 : String?
activePaymentChannels   : Set<String>
branches                : List<BranchContext>
```

### BranchContext (new — embedded in MerchantContext)
```
branchId                : UUID
name                    : String
distanceMeters          : Double?
addressLine1            : String?
addressLine2            : String?
city                    : String?
postalCode              : String?
country                 : String?
contactFullName         : String?
contactEmail            : String?
contactPhoneNumber      : String?
activePaymentChannels   : Set<String>
```

### NearbyMerchantResponse (enhanced — additive)
```
(existing fields unchanged)
+ activeBranches  : List<NearbyBranchResponse>   — empty list if no active branches with location
```

### NearbyBranchResponse (new)
```
branchId                : UUID
name                    : String
distanceMeters          : Double
latitude                : Double
longitude               : Double
addressLine1            : String?
city                    : String?
postalCode              : String?
country                 : String?
contactFullName         : String?
contactEmail            : String?
contactPhoneNumber      : String?
activePaymentChannels   : Set<String>
imageUrl                : String?
```
