# Research: Geofence-Driven Nearby Merchant Discovery

**Branch**: `008-geofence-nearby-merchants` | **Phase**: 0 | **Date**: 2026-04-24

---

## 1. Prototype Behaviour (source of truth)

**Decision**: Mirror the prototype's interaction model exactly — mobile detects region entry via OS geofencing, POSTs a proximity event, server responds with merchant context.

**Findings from prototype**:
- Mobile uses `expo-location` OS geofencing (`startGeofencingAsync`) with a fallback to 30-second polling
- Regions are hardcoded in `src/lib/geofenceRegions.ts` — this is the gap being closed
- Client-side cooldown: 30 seconds per `{regionId}:{eventType}` key stored in device storage
- iOS hard limit: max 20 simultaneously registered geofence regions; prototype sorts by proximity and takes the top 20
- Proximity POST payload: `{ userId, merchantId, event, location: {lat, lng}, timestamp }`
- Server response: `{ recorded: boolean, action: 'notify' | 'none' }`
- `action: notify` triggers a local push notification on device; no separate server push is needed for this flow

**Rationale**: The prototype's client architecture is sound and does not need to change. Only the server side is being built out.

---

## 2. Server-Side Deduplication Strategy

**Decision**: Server-side deduplication by querying `MAX(received_at)` for `(user_token OR device_id, region_id, event_type)` within the cooldown window (30 seconds, configurable). If a matching record exists within the window, return `recorded: false` immediately.

**Rationale**: The client already applies a 30-second cooldown in device storage, but this is not reliable (storage cleared, app reinstall, multiple devices). Server-side deduplication is the authoritative guard. Storing this as a simple query on the `proximity_events` table avoids a Redis dependency.

**Alternatives considered**:
- Redis TTL cache per `{userId}:{regionId}:{eventType}` key: rejected — adds infrastructure dependency for a feature that works fine with a DB query on a small time window.
- Unique constraint on the table: rejected — two events 31 seconds apart are both valid; uniqueness is time-windowed, not absolute.

---

## 3. Per-User Geofence State Tracking

**Decision**: Do NOT maintain a real-time server-side "user is currently inside region X" table. Record proximity events only. The mobile owns live in/out state.

**Rationale**:
- Exit events are unreliable on mobile (app kill, Airplane mode, OS budget restrictions)
- A "currently inside" state table would become stale and misleading
- All server-side needs (deduplication, analytics, "was this user near a merchant recently") are served by querying the proximity events log
- Privacy: not tracking live location reduces data sensitivity

**Alternatives considered**:
- `user_geofence_sessions` table with open/close events: rejected — complexity without clear benefit for v1; exit events can't be trusted to close sessions reliably.

---

## 4. Geofence Region Source

**Decision**: Geofence regions are derived at query time from two existing sources — `merchant_applications` (with location set) and `merchant_branches` (with location set). No new "geofence region" table is needed.

**Rationale**: The region identity is already encoded in the existing tables. Adding a separate region table would duplicate data. The `region_id` format encodes the source: `merchant:{uuid}` or `branch:{uuid}`.

**Region ID format**: `merchant:{merchantApplicationId}` or `branch:{merchantBranchId}`. This makes region IDs stable, globally unique, and self-describing — the server can parse a region ID from a proximity event to look up the owning entity without additional mapping tables.

---

## 5. Module Placement (Spring Modulith)

**Decision**: All new code lives within the existing `merchant` module. New files are added to existing sub-packages (`api`, `application`, `domain`, `repository`, `web`) following the established pattern. No new top-level module is created.

**Rationale**: Geofencing is merchant data — it queries and enriches `MerchantApplication` and `MerchantBranch` records. Creating a separate module would require cross-module event coupling for what is essentially a read-heavy query feature. The `merchant` module already contains location, nearby, and branch logic.

**Alternatives considered**:
- New `geofence` top-level module: rejected — would need to either duplicate data or create cross-module dependencies for a feature that is entirely merchant-data-driven.
- Named interface `@NamedInterface("geofence")` within merchant: considered but unnecessary overhead for an intra-module feature with no external consumers yet.

---

## 6. Nearby Merchants Enhancement Strategy

**Decision**: Enhance the existing `NearbyMerchantResponse` model to embed an `activeBranches` list. The branch list is populated in the same service call by querying `MerchantBranchRepository` for active branches belonging to the returned merchants. Branch distance is calculated via the existing Haversine utility.

**Rationale**: Keeps the contract backward-compatible by adding a new field (additive change). The existing `GET /v1/merchants/nearby` path is unchanged; only the response shape grows.

**Performance note**: Avoid N+1 — fetch all branches for the returned merchant IDs in a single query using `findAllByMerchantApplicationIdInAndActiveTrue`.

---

## 7. Proximity Response — Merchant Context Depth

**Decision**: The proximity endpoint returns the full merchant context needed by the payment flow: merchant name, logo URL (presigned), active payment channels, and a list of active branches with name, address, contact, payment channels. This avoids a second API call from the mobile.

**Rationale**: Mobile geofence tasks run in the background with tight OS time budgets. Reducing to a single server round-trip is more reliable than chaining two calls.

---

## 8. Cooldown Configuration

**Decision**: Cooldown window exposed as `blitzpay.geofence.proximity-cooldown-seconds` in `application.yml`, defaulting to 30. Bound via `@ConfigurationProperties`.

**Rationale**: Matches prototype's 30-second client-side cooldown. Being configurable allows tuning without code change.
