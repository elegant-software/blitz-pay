# Data Model: MCP Bulk Operations

No new tables or columns. All changes are additive API/tool-layer types on top of the existing schema.

---

## Existing entities (unchanged)

### `merchant_products` table → `MerchantProduct` entity

Relevant existing columns used by this feature:

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `merchant_application_id` | UUID FK | tenant scope |
| `merchant_branch_id` | UUID FK | branch scope |
| `name` | VARCHAR | unique within branch (enforced at service layer) |
| `unit_price` | NUMERIC(12,4) | must be ≥ 0 |
| `description` | VARCHAR(2000) | nullable |
| `product_code` | BIGINT | nullable; auto-assigned if omitted |
| `product_category_id` | UUID FK nullable | already present; the gap was only in the MCP tool |
| `active` | BOOLEAN | |
| `status` | VARCHAR(32) | |

### `merchant_product_categories` table → `MerchantProductCategory` entity

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `merchant_application_id` | UUID FK | tenant scope |
| `name` | VARCHAR(100) | unique within merchant (case-insensitive) |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

## New API types (in `merchant/api/`)

### `BulkProductInput`

Input item for `products_bulk_create`. Placed in `MerchantProductModels.kt`.

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `productName` | String | Yes | non-blank |
| `unitPrice` | String | Yes | parseable as BigDecimal ≥ 0 |
| `description` | String? | No | ≤ 2000 chars if present |
| `productCode` | String? | No | parseable as positive Long if present |
| `categoryId` | String? | No | parseable as UUID; must exist for merchant if present |

### `BulkCategoryInput`

Input item for `categories_bulk_create`. Placed in `MerchantProductCategoryModels.kt`.

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `categoryName` | String | Yes | non-blank, ≤ 100 chars |

### `BulkSkippedItem`

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | product name or category name |
| `reason` | String | human-readable, e.g. "already exists" or "duplicate within batch" |
| `existingId` | String? | ID of the existing record, when available |

### `BulkFailedItem`

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | product name or category name |
| `reason` | String | validation or service error message |

### `BulkProductCreateResult`

| Field | Type | Notes |
|-------|------|-------|
| `created` | List\<ProductResponse\> | successfully created items with full response |
| `skipped` | List\<BulkSkippedItem\> | already-existed or within-batch duplicates |
| `failed` | List\<BulkFailedItem\> | validation errors or unexpected service errors |

### `BulkCategoryCreateResult`

| Field | Type | Notes |
|-------|------|-------|
| `created` | List\<ProductCategoryResponse\> | successfully created categories |
| `skipped` | List\<BulkSkippedItem\> | already-existed or within-batch duplicates |
| `failed` | List\<BulkFailedItem\> | validation errors or unexpected service errors |

---

## Relationships

```
BulkProductCreateResult
  ├── created: List<ProductResponse>       (existing type, unchanged)
  ├── skipped: List<BulkSkippedItem>       (new)
  └── failed:  List<BulkFailedItem>        (new)

BulkCategoryCreateResult
  ├── created: List<ProductCategoryResponse>  (existing type, unchanged)
  ├── skipped: List<BulkSkippedItem>          (new)
  └── failed:  List<BulkFailedItem>           (new)
```

---

## Validation rules (enforced at MCP tool boundary)

- Batch size: `require(items.size <= 200)` — entire request rejected if exceeded
- Within-batch name deduplication: tracked with `LinkedHashSet<String>`; second occurrence → `skipped` with `reason = "duplicate within batch"`
- Per-item field validation: delegated to `MerchantProductService.create()` and `MerchantProductCategoryService.create()` (existing rules)
- `categoryId` presence check for products: `MerchantProductService.validateCategory()` already handles unknown IDs
