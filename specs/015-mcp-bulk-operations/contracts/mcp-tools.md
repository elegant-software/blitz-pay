# MCP Tool Contracts: Bulk Operations

These tools extend the existing `merchant` module MCP server. They follow the conventions in
`reference/mcp-server-best-practices.md`.

---

## `products_bulk_create`

**Purpose**: Create multiple products for a merchant branch in a single call. Returns a
structured result distinguishing created, skipped (duplicates), and failed items.

**Mutates state**: Yes.

### Input

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `merchantId` | String (UUID) | Yes | ID of the merchant |
| `branchId` | String (UUID) | Yes | ID of the branch within the merchant |
| `products` | List\<BulkProductInput\> | Yes | 1–200 product definitions |

#### `BulkProductInput` fields

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `productName` | String | Yes | non-blank |
| `unitPrice` | String | Yes | parseable as decimal ≥ 0 |
| `description` | String? | No | ≤ 2000 characters |
| `productCode` | String? | No | positive integer; auto-assigned if omitted |
| `categoryId` | String? | No | UUID of an existing category for this merchant |

### Output: `BulkProductCreateResult`

```json
{
  "created": [
    {
      "productId": "uuid",
      "branchId": "uuid",
      "name": "Tomatensuppe",
      "description": "Creamy tomato soup",
      "unitPrice": 6.00,
      "imageUrl": null,
      "active": false,
      "status": "INACTIVE",
      "categoryId": "uuid-or-null",
      "categoryName": "Starters",
      "productCode": 1,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "skipped": [
    {
      "name": "Wiener Schnitzel",
      "reason": "already exists",
      "existingId": "uuid"
    }
  ],
  "failed": [
    {
      "name": "Bad Product",
      "reason": "unitPrice must be >= 0"
    }
  ]
}
```

### Failure modes

| Condition | Behaviour |
|-----------|-----------|
| `merchantId` not found | `IllegalArgumentException` thrown — entire request rejected |
| `branchId` not found or not belonging to merchant | `IllegalArgumentException` thrown — entire request rejected |
| `products` list is empty | Returns result with empty `created`, `skipped`, and `failed` |
| `products` list has > 200 items | `IllegalArgumentException` thrown — entire request rejected |
| Invalid `merchantId` or `branchId` UUID format | `IllegalArgumentException` thrown |
| Invalid `categoryId` UUID format per item | Item goes to `failed` with parse error message |
| Duplicate `productName` within the batch | Second occurrence goes to `skipped` with `reason = "duplicate within batch"` |
| Product with same name already exists for branch | Goes to `skipped` with `reason = "already exists"` and `existingId` set |
| Validation error (blank name, negative price, etc.) | Item goes to `failed` with reason from service layer |

---

## `categories_bulk_create`

**Purpose**: Create multiple product categories for a merchant in a single call. Returns a
structured result distinguishing created, skipped (duplicates), and failed items.

**Mutates state**: Yes.

### Input

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `merchantId` | String (UUID) | Yes | ID of the merchant |
| `categories` | List\<BulkCategoryInput\> | Yes | 1–200 category definitions |

#### `BulkCategoryInput` fields

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `categoryName` | String | Yes | non-blank, ≤ 100 characters |

### Output: `BulkCategoryCreateResult`

```json
{
  "created": [
    {
      "id": "uuid",
      "name": "Starters",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "skipped": [
    {
      "name": "Mains",
      "reason": "already exists",
      "existingId": "uuid"
    }
  ],
  "failed": [
    {
      "name": "",
      "reason": "Category name must not be blank"
    }
  ]
}
```

### Failure modes

| Condition | Behaviour |
|-----------|-----------|
| `merchantId` not found | `IllegalArgumentException` thrown — entire request rejected |
| `categories` list is empty | Returns result with empty lists |
| `categories` list has > 200 items | `IllegalArgumentException` thrown — entire request rejected |
| Invalid `merchantId` UUID format | `IllegalArgumentException` thrown |
| Duplicate `categoryName` within the batch (case-insensitive) | Second occurrence goes to `skipped` with `reason = "duplicate within batch"` |
| Category with same name already exists for merchant | Goes to `skipped` with `reason = "already exists"` and `existingId` set |
| Validation error (blank name, > 100 chars) | Item goes to `failed` with reason from service layer |

---

## `product_id_by_name_or_create` — updated

**Change**: Adds `categoryId: String? = null` parameter.

| New Parameter | Type | Required | Description |
|---------------|------|----------|-------------|
| `categoryId` | String? (UUID) | No | Assigns the product to a category when creating. Ignored when the product already exists (unless updating the image). |

This is a backward-compatible, additive change. Existing callers that omit `categoryId` continue to work unchanged.

---

## Unchanged tools

All other tools in `MerchantProductTools` are unaffected:
`merchant_product_update`, `merchant_id_by_name`, `merchant_id_by_name_or_create`,
`branch_id_by_name`, `branch_id_by_name_or_create`, `category_id_by_name`,
`category_id_by_name_or_create`, `merchant_list_product_categories`, `product_id_by_name`.
