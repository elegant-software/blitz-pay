# Feature Specification: Geofence-Driven Nearby Merchant Discovery

**Feature Branch**: `008-geofence-nearby-merchants`  
**Created**: 2026-04-24  
**Status**: Draft

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mobile App Fetches Geofence Regions from Server (Priority: P1)

When a user opens the app (or enables location monitoring), the mobile fetches the full list of active merchant and branch geofence regions from the server instead of using a hardcoded static list. The server returns each region's coordinates, radius, and identifiers. The mobile registers up to 20 of the nearest regions with the OS geofencing API.

**Why this priority**: The prototype hardcodes merchant regions, making it impossible to add or change merchants without a mobile release. Moving region data server-side unblocks all other stories and is the foundation for the feature.

**Independent Test**: Can be tested by calling the regions endpoint and verifying it returns active merchant/branch coordinates and radii. Delivers immediate value: mobile no longer needs a release to reflect new merchants.

**Acceptance Scenarios**:

1. **Given** active merchants and branches have location data set, **When** the mobile calls the geofence regions endpoint, **Then** a list of regions is returned, each including a unique region identifier, coordinates, radius, and a human-readable label.
2. **Given** a user's current position is provided, **When** the mobile calls the geofence regions endpoint with that position, **Then** regions are ordered by distance (nearest first) so the mobile can take the top 20 for OS registration.
3. **Given** a merchant or branch is marked inactive, **When** the geofence regions endpoint is called, **Then** that merchant or branch is excluded from the response.
4. **Given** a branch has its own location, **When** regions are returned, **Then** the branch appears as its own region entry (distinct from the parent merchant's region), carrying the branch's own radius.

---

### User Story 2 - Server Records Proximity Event and Returns Merchant Context (Priority: P1)

When the mobile detects a geofence entry, it posts a proximity event to the server. The server records the event, deduplicates within a cooldown window, and returns full merchant and branch context so the mobile can display merchant details (name, address, logo, active payment channels) without a second API call.

**Why this priority**: This is the core data contract between mobile geofencing and the server. Without it, the app cannot show contextual merchant information when a user walks into range.

**Independent Test**: Can be tested by POSTing a proximity event and verifying the response contains merchant name, branches, payment channels, and an action flag. No UI needed.

**Acceptance Scenarios**:

1. **Given** a user enters a registered geofence region, **When** the mobile posts an `enter` event with the region identifier and user coordinates, **Then** the server responds with `recorded: true`, an `action` field (`notify` or `none`), and a `merchant` object including name, active branches, and payment channels.
2. **Given** a proximity event was already recorded for the same user, region, and event type within the last 30 seconds, **When** the mobile posts another `enter` event, **Then** the server responds with `recorded: false` and `action: none` (deduplication).
3. **Given** an unauthenticated request, **When** the mobile posts a proximity event, **Then** the server still records the event and returns merchant context (anonymous proximity is permitted).
4. **Given** a region identifier that no longer matches an active merchant or branch, **When** a proximity event is posted, **Then** the server returns `recorded: false` and `action: none` without error.
5. **Given** the server receives an `exit` event, **When** the event is processed, **Then** the event is recorded but `action` is always `none` (exit events do not trigger notifications or return merchant context).

---

### User Story 3 - Nearby Merchants Endpoint Returns Branch-Level Context (Priority: P2)

When the user opens the merchant discovery screen or the app needs to display nearby options, the existing "nearby merchants" endpoint returns each merchant together with its active branches, including branch addresses, contact details, and active payment channels.

**Why this priority**: Currently the nearby endpoint only returns merchant-level data. Branch-level context (which branch is closest, which payment channels it supports) is what the payment flow actually needs.

**Independent Test**: Can be tested by calling the nearby endpoint and verifying each merchant result includes an embedded list of active branches with location and payment channel data.

**Acceptance Scenarios**:

1. **Given** a merchant has multiple active branches with location data, **When** the nearby endpoint is called, **Then** each merchant result includes a `branches` list, each branch entry containing its own distance from the caller, address, payment channels, and contact info.
2. **Given** a branch is inactive, **When** the nearby endpoint is called, **Then** inactive branches are excluded from the embedded branch list.
3. **Given** a merchant has no active branches with location data, **When** it appears in nearby results, **Then** the `branches` list is empty and the merchant-level location is still returned.

---

### Edge Cases

- User posts a proximity event with coordinates far outside the declared geofence radius — server records the event as-is (coordinate validation is the mobile's responsibility).
- Region list is requested when no merchants have location data set — returns an empty list with 200 OK.
- User's device OS geofencing fires an `enter` and `exit` in rapid succession (e.g., walking along a boundary) — deduplication cooldown prevents repeated recordings and notifications.
- Mobile requests regions without providing a position — returns all active regions in an unordered list.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose an endpoint that returns the list of active geofence regions (one per merchant location and one per branch location that has coordinates), each including a unique region ID, display name, coordinates, and radius.
- **FR-002**: When caller provides current coordinates, the geofence regions endpoint MUST return regions ordered by ascending distance from that position.
- **FR-003**: System MUST expose an endpoint to record a proximity event (enter or exit) for a given region and user.
- **FR-004**: The proximity endpoint MUST deduplicate: if the same user, region, and event type have been recorded within a configurable cooldown window (default 30 seconds), it MUST return `recorded: false` and skip persistence.
- **FR-005**: On a successfully recorded `enter` event, the proximity endpoint MUST return the full merchant context: name, logo storage key, active payment channels, and a list of active branches (with each branch's name, address, contact, and payment channels).
- **FR-006**: The proximity endpoint MUST accept unauthenticated requests; authenticated and anonymous events are both recorded.
- **FR-007**: The existing nearby merchants endpoint MUST be enhanced to include active branches in each result, with each branch carrying distance, address, contact details, and active payment channels.
- **FR-008**: System MUST persist each proximity event (user token or anonymous marker, region ID, event type, coordinates, timestamp) for analytics and deduplication.
- **FR-009**: Inactive merchants and inactive branches MUST be excluded from both the geofence regions list and the nearby merchants response.
- **FR-010**: The proximity endpoint MUST return `action: notify` for an `enter` event that is recorded and where the merchant has at least one active payment channel; otherwise `action: none`.

### Key Entities

- **Geofence Region**: A server-defined circular area associated with a merchant or branch. Attributes: unique region ID, source (merchant or branch), display name, coordinates, radius in metres, active flag.
- **Proximity Event**: A recorded occurrence of a user entering or exiting a region. Attributes: anonymous or user identifier, region ID, event type (enter/exit), reported coordinates, server-received timestamp.
- **Nearby Merchant Result**: An enriched merchant record including distance from caller, merchant metadata, and an embedded list of active branches with their own distances and payment context.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Mobile app can discover new or changed merchant geofence regions without a new app release — verified by adding a merchant and confirming it appears in the regions endpoint response within one server restart cycle.
- **SC-002**: A proximity event POST and response round-trip completes in under 500 ms at the 95th percentile under normal load.
- **SC-003**: Deduplication reduces duplicate proximity event records to zero within the cooldown window — verified by sending the same event twice within 30 seconds and confirming only one record is stored.
- **SC-004**: The nearby endpoint returns merchant and branch data sufficient for the mobile payment flow without any additional API calls — verified by checklist of required fields (name, payment channels, branch address).
- **SC-005**: Zero hardcoded merchant coordinates remain in the mobile codebase after this feature is complete.

---

## Assumptions

- The cooldown window default of 30 seconds mirrors the prototype client-side cooldown and is configurable via application properties without code change.
- The iOS limit of 20 simultaneously registered geofence regions is handled entirely by the mobile client (sort by proximity, take top 20); the server returns all active regions.
- "User identifier" for proximity events is the bearer token subject when authenticated, and a stable anonymous device identifier when not. The server does not reject anonymous events.
- Branch locations are the primary geofence anchors in production; merchant-level locations serve as a fallback for merchants that have no branches with coordinates.
- `action: notify` is the signal for the mobile to schedule a local push notification. The push notification content (merchant name, branch info) is derived from the merchant context in the same proximity response — no second call needed.
- Real-time server-side tracking of whether a user is currently "inside" a region is out of scope for this version. The server records discrete events; the mobile owns the live in/out state.
- Exit events are recorded for completeness but do not trigger any action or return merchant context.
