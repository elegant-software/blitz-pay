# API Contract: Merchant Product Catalog

**Feature Branch**: `001-merchant-onboarding`
**Date**: 2026-04-19
**Module**: `merchant`
**Base path**: `/v1/merchants/{merchantId}/products`

---

## Security

All endpoints require an authenticated principal. `{merchantId}` in the path is validated against the principal's merchant reference:
- `MERCHANT_APPLICANT` role: may only access their own `{merchantId}`.
- `OPERATIONS_REVIEWER` / `SYSTEM` role: may access any `{merchantId}`.

Requests where `{merchantId}` does not match the principal's entitlement return `403 Forbidden`.

---

## Endpoints

### 1. Create Product

**`POST /v1/merchants/{merchantId}/products`**

Creates a new active product for the given merchant.

#### Request

```json
{
  "name": "Artisan Coffee Blend 250g",
  "unitPrice": 12.50,
  "imageUrl": "https://cdn.blitzpay.io/merchant/abc123/products/img1.jpg"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `name` | string | YES | 1–255 characters |
| `unitPrice` | number | YES | ≥ 0, up to 4 decimal places |
| `imageUrl` | string | NO | Valid HTTPS URL, ≤ 2048 characters |

#### Response `201 Created`

```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "7b3d9f00-1234-4abc-8765-000000000001",
  "name": "Artisan Coffee Blend 250g",
  "unitPrice": 12.50,
  "imageUrl": "https://cdn.blitzpay.io/merchant/abc123/products/img1.jpg",
  "active": true,
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T10:00:00Z"
}
```

#### Error responses

| Status | Condition |
|--------|-----------|
| `400 Bad Request` | `name` blank, `unitPrice` negative or non-numeric |
| `403 Forbidden` | Principal not entitled to `{merchantId}` |
| `404 Not Found` | `{merchantId}` does not exist |

---

### 2. List Active Products

**`GET /v1/merchants/{merchantId}/products`**

Returns all active products for the merchant. Inactive (soft-deleted) products are excluded.

#### Response `200 OK`

```json
{
  "merchantId": "7b3d9f00-1234-4abc-8765-000000000001",
  "products": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Artisan Coffee Blend 250g",
      "unitPrice": 12.50,
      "imageUrl": "https://cdn.blitzpay.io/merchant/abc123/products/img1.jpg",
      "active": true,
      "createdAt": "2026-04-19T10:00:00Z",
      "updatedAt": "2026-04-19T10:00:00Z"
    }
  ]
}
```

Empty catalog returns `products: []`, not 404.

---

### 3. Get Product

**`GET /v1/merchants/{merchantId}/products/{productId}`**

Returns a single product. Returns `404` for both non-existent and inactive (soft-deleted) products to prevent enumeration.

#### Response `200 OK`

```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "7b3d9f00-1234-4abc-8765-000000000001",
  "name": "Artisan Coffee Blend 250g",
  "unitPrice": 12.50,
  "imageUrl": "https://cdn.blitzpay.io/merchant/abc123/products/img1.jpg",
  "active": true,
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T10:00:00Z"
}
```

#### Error responses

| Status | Condition |
|--------|-----------|
| `403 Forbidden` | Principal not entitled to `{merchantId}` |
| `404 Not Found` | Product not found or soft-deleted |

---

### 4. Update Product

**`PUT /v1/merchants/{merchantId}/products/{productId}`**

Replaces all mutable fields (name, unitPrice, imageUrl). Passing `null` for `imageUrl` removes the existing image.

#### Request

```json
{
  "name": "Artisan Coffee Blend 500g",
  "unitPrice": 22.00,
  "imageUrl": null
}
```

#### Response `200 OK`

Same shape as Create response, with updated `updatedAt`.

#### Error responses

| Status | Condition |
|--------|-----------|
| `400 Bad Request` | Validation failure |
| `403 Forbidden` | Principal not entitled |
| `404 Not Found` | Product not found or soft-deleted |

---

### 5. Deactivate Product (Soft Delete)

**`DELETE /v1/merchants/{merchantId}/products/{productId}`**

Sets `active = false`. The product is retained in the database and is no longer returned by list/get operations.

#### Response `204 No Content`

No body.

#### Error responses

| Status | Condition |
|--------|-----------|
| `403 Forbidden` | Principal not entitled |
| `404 Not Found` | Product not found or already inactive |

---

## Common Headers

| Header | Required | Notes |
|--------|----------|-------|
| `Content-Type: application/json` | YES for POST/PUT | |
| `Accept: application/json` | Recommended | |

---

## Tenant Isolation Guarantee

Every response for `GET /v1/merchants/{merchantId}/products` contains only products where `merchant_application_id = {merchantId}`:
- Enforced at application layer via Hibernate `tenantFilter`
- Enforced at DB layer via PostgreSQL RLS policy `merchant_tenant_isolation`

A request that receives a `200 OK` listing will never contain products from a different merchant.
