# Quickstart: MCP Bulk Operations

## What's changing

1. **New tool `categories_bulk_create`** — create many categories in one call.
2. **New tool `products_bulk_create`** — create many products in one call, with per-item success/skip/fail reporting.
3. **Fix `product_id_by_name_or_create`** — now accepts `categoryId` so newly created products can be assigned to a category.

No new database migrations, no new Liquibase changesets, no new Spring beans beyond what already exists.

---

## Typical onboarding flow using the new tools

```
1. merchant_id_by_name_or_create(merchantName="Zur Alten Post")
   → merchantId

2. branch_id_by_name_or_create(merchantId, branchName="Hauptfiliale")
   → branchId

3. categories_bulk_create(merchantId, categories=[
       {categoryName:"Vorspeisen"},
       {categoryName:"Hauptgerichte"},
       {categoryName:"Desserts"},
       {categoryName:"Getränke"}
   ])
   → BulkCategoryCreateResult
       .created → [{id: "uuid-starters", name:"Vorspeisen"}, ...]

4. products_bulk_create(merchantId, branchId, products=[
       {productName:"Tomatensuppe", unitPrice:"6.00", categoryId:"uuid-starters", productCode:"1"},
       {productName:"Wiener Schnitzel", unitPrice:"18.50", categoryId:"uuid-mains", productCode:"2"},
       ...
   ])
   → BulkProductCreateResult
       .created → [...]
       .skipped → []
       .failed  → []
```

---

## Files to change

### New types — `merchant/api/`

**`MerchantProductModels.kt`** — append:
```kotlin
data class BulkProductInput(
    val productName: String,
    val unitPrice: String,
    val description: String? = null,
    val productCode: String? = null,
    val categoryId: String? = null,
)

data class BulkSkippedItem(val name: String, val reason: String, val existingId: String? = null)
data class BulkFailedItem(val name: String, val reason: String)

data class BulkProductCreateResult(
    val created: List<ProductResponse>,
    val skipped: List<BulkSkippedItem>,
    val failed: List<BulkFailedItem>,
)
```

**`MerchantProductCategoryModels.kt`** — append:
```kotlin
data class BulkCategoryInput(val categoryName: String)

data class BulkCategoryCreateResult(
    val created: List<ProductCategoryResponse>,
    val skipped: List<BulkSkippedItem>,
    val failed: List<BulkFailedItem>,
)
```

### Updated MCP tool — `merchant/mcp/MerchantProductTool.kt`

1. Add `categoryId: String? = null` parameter to `getOrCreateProductId`; forward to `CreateProductRequest`.
2. Add `bulkCreateCategories(@McpTool)` method.
3. Add `bulkCreateProducts(@McpTool)` method.

### Tests — `src/test/kotlin/.../merchant/mcp/MerchantProductToolsTest.kt`

Extend existing test class. New test cases:
- `getOrCreateProductId passes categoryId into create request`
- `bulkCreateCategories creates all when none exist`
- `bulkCreateCategories skips duplicates within merchant`
- `bulkCreateCategories skips within-batch duplicates`
- `bulkCreateCategories rejects batch over 200`
- `bulkCreateCategories returns failure for invalid item`
- `bulkCreateProducts creates all when none exist`
- `bulkCreateProducts skips products that already exist by name`
- `bulkCreateProducts skips within-batch name duplicates`
- `bulkCreateProducts rejects batch over 200`
- `bulkCreateProducts fails item with invalid price`
- `bulkCreateProducts passes categoryId through to create request`

---

## Running the tests

```bash
./gradlew test --tests "com.elegant.software.blitzpay.merchant.mcp.MerchantProductToolsTest"
./gradlew check
```
