# Contract: Merchant Location

**Feature Branch**: `001-merchant-onboarding`
**Date**: 2026-04-19
**Scope**: `PUT /v1/merchants/{merchantId}/location` (upsert), `GET /v1/merchants/{merchantId}/location`

---

## Endpoints

### PUT /v1/merchants/{merchantId}/location

Upsert the geographic location for a merchant. Creates the record on first call; replaces it on subsequent calls. Sending all-null fields clears the location data while preserving the record.

**Request**

```http
PUT /v1/merchants/{merchantId}/location
Content-Type: application/json
```

```json
{
  "latitude": 53.0793,
  "longitude": 8.8017,
  "googlePlaceId": "ChIJuXHBrNcOrkcRrRpMbW97Uus"
}
```

All fields are optional. Valid combinations:

| `latitude` | `longitude` | `googlePlaceId` | Result |
|------------|-------------|-----------------|--------|
| number | number | string or null | Set coordinates (and optional Place ID) |
| null | null | string or null | Clear coordinates; keep/set Place ID |
| null | null | null | Clear all location data |
| number | null | any | **400 Bad Request** — lat without lon |
| null | number | any | **400 Bad Request** — lon without lat |

**Responses**

`201 Created` — first write (no prior location record existed):

```json
{
  "merchantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "latitude": 53.0793,
  "longitude": 8.8017,
  "googlePlaceId": "ChIJuXHBrNcOrkcRrRpMbW97Uus",
  "createdAt": "2026-04-19T14:32:00Z",
  "updatedAt": "2026-04-19T14:32:00Z"
}
```

`200 OK` — update (location record already existed):

```json
{
  "merchantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "latitude": 53.0793,
  "longitude": 8.8017,
  "googlePlaceId": "ChIJuXHBrNcOrkcRrRpMbW97Uus",
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T14:32:00Z"
}
```

`400 Bad Request` — validation failure:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "latitude and longitude must both be provided or both be null"
}
```

`404 Not Found` — merchant does not exist.

---

### GET /v1/merchants/{merchantId}/location

Retrieve the location record for a merchant.

**Request**

```http
GET /v1/merchants/{merchantId}/location
```

**Responses**

`200 OK` — location record exists:

```json
{
  "merchantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "latitude": 53.0793,
  "longitude": 8.8017,
  "googlePlaceId": "ChIJuXHBrNcOrkcRrRpMbW97Uus",
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T14:32:00Z"
}
```

`404 Not Found` — merchant does not exist, or merchant has no location record.

---

## Validation Rules

| Field | Rule |
|-------|------|
| `latitude` | `null` or `DECIMAL` in range `[-90, 90]` |
| `longitude` | `null` or `DECIMAL` in range `[-180, 180]` |
| `latitude` + `longitude` | Both present or both null (co-requirement) |
| `googlePlaceId` | `null` or non-empty `VARCHAR(255)`; stored as-is without Maps API validation |

---

## Tenant Isolation

- The `{merchantId}` path variable identifies both the target merchant and the tenant scope.
- The authenticated principal must be the merchant identified by `{merchantId}` or hold an internal admin role.
- No Hibernate tenant filter is applied to `MerchantLocation` (the query uses the explicit `merchant_application_id` column in the repository finder).
