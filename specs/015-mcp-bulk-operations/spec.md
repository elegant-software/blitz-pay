# Feature Specification: MCP Bulk Operations for Products and Categories

**Feature Branch**: `015-mcp-bulk-operations`  
**Created**: 2026-05-09  
**Status**: Draft  
**Input**: User description: "products_bulk_create and categories_bulk_create MCP tools to reduce round trips for list-shaped operations (menus, inventory, catalog imports) and make AI-assisted onboarding smoother."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bulk Product Creation During Merchant Onboarding (Priority: P1)

An AI agent is onboarding a new restaurant merchant. The restaurant has a menu of 40 items across several categories. Instead of calling a single-item create endpoint 40 times, the agent calls `products_bulk_create` once with the full product list scoped to the merchant and branch. All valid products are created, and any invalid entries are reported back with reasons.

**Why this priority**: This is the core use case — eliminating the N+1 call pattern that makes catalog onboarding slow and brittle. A single failed request for one item today blocks or complicates the entire flow.

**Independent Test**: Can be fully tested by calling `products_bulk_create` with a mixed list of valid and invalid products and verifying that valid products are created and errors are returned per item — no categories or other endpoints required.

**Acceptance Scenarios**:

1. **Given** a valid merchant and branch exist, **When** the agent submits a list of 40 products with all valid data, **Then** all 40 products are created and their identifiers are returned in the response.
2. **Given** a valid merchant and branch exist, **When** the agent submits a list of 10 products where 2 have duplicate product codes that already exist, **Then** 8 products are created, the 2 duplicates are rejected with a clear "already exists" reason, and the remaining 8 are accessible immediately.
3. **Given** a valid merchant and branch exist, **When** the agent submits a list where one product has a negative unit price, **Then** that item is rejected with a validation error, all other valid items are created, and the response clearly identifies which items failed.
4. **Given** a valid merchant and branch exist, **When** the agent submits an empty product list, **Then** the operation returns a successful response with zero items created.

---

### User Story 2 - Bulk Category Creation Before Product Assignment (Priority: P2)

Before loading products, the AI agent needs to establish the category structure (e.g., "Starters", "Mains", "Desserts", "Drinks"). The agent calls `categories_bulk_create` once with the full category list, then uses the returned category identifiers to assign products in the subsequent bulk product creation.

**Why this priority**: Categories are a prerequisite for structured product catalogs. Bulk category creation unlocks the full onboarding flow but can be demonstrated independently.

**Independent Test**: Can be fully tested by calling `categories_bulk_create` with a list of categories and verifying that all are created and identifiers returned — independent of product creation.

**Acceptance Scenarios**:

1. **Given** a valid merchant exists, **When** the agent submits a list of 6 category names, **Then** all 6 categories are created, each with a unique identifier returned in the response.
2. **Given** a valid merchant exists and "Starters" already exists, **When** the agent submits a list containing "Starters" and 3 new categories, **Then** the 3 new categories are created and the duplicate "Starters" is reported with an "already exists" reason.
3. **Given** a valid merchant exists, **When** two entries in the category list have the same name, **Then** the first is created and the second is rejected as a duplicate within the same request.

---

### User Story 3 - Catalog Re-import and Idempotent Updates (Priority: P3)

A merchant updates their seasonal menu. The AI agent re-submits a bulk product list that includes both existing items (to be updated or skipped) and new items. The agent relies on the response to understand which items are new, which were skipped, and which failed.

**Why this priority**: Re-import scenarios are common in practice (seasonal menus, price changes), but the core P1/P2 flows deliver value without this.

**Independent Test**: Can be tested by submitting a bulk list where half the products already exist, and verifying the response distinguishes between created, skipped/already-exists, and failed entries.

**Acceptance Scenarios**:

1. **Given** 20 products already exist for a branch, **When** the agent submits a bulk list of those same 20 products with no changes, **Then** all 20 are reported as "already exists" (skipped), none are duplicated, and the operation completes without error.
2. **Given** a mix of existing and new products is submitted, **Then** the response clearly separates created items from skipped items from failed items.

---

### Edge Cases

- What happens when the merchant ID does not exist or the caller lacks permission for that merchant?
- How does the system handle a product list that exceeds the maximum allowed batch size?
- What if network interruption occurs mid-batch — are partial results visible to callers?
- How does the system handle a product with a null or blank product name?
- What if the same product code appears twice within a single bulk request?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose a `products_bulk_create` operation that accepts a merchant identifier, a branch identifier, and a list of product definitions in a single call.
- **FR-002**: System MUST expose a `categories_bulk_create` operation that accepts a merchant identifier and a list of category definitions in a single call.
- **FR-003**: System MUST validate each item in the list independently and report per-item validation errors without aborting the entire batch.
- **FR-004**: System MUST create all valid items in the batch even when some items are invalid (partial-success semantics).
- **FR-005**: System MUST return a structured response distinguishing created items, skipped/duplicate items, and failed items, each with an identifier and reason.
- **FR-006**: System MUST reject any item whose required fields (product code, product name, unit price for products; category name for categories) are absent or blank.
- **FR-007**: System MUST reject items with non-positive unit prices.
- **FR-008**: System MUST reject items whose codes or names conflict with existing records within the same merchant/branch scope, reporting them as duplicates rather than treating the batch as failed.
- **FR-009**: System MUST enforce a maximum batch size of 200 items per request; requests exceeding this limit MUST be rejected entirely with a clear error before any items are processed.
- **FR-010**: System MUST make successfully created items available immediately upon response.
- **FR-011**: System MUST scope all product operations to both a merchant and a branch; all category operations MUST be scoped to a merchant.

### Key Entities

- **Product**: A sellable item belonging to a branch within a merchant. Key attributes: product code (unique within branch), product name, description (optional), unit price.
- **Category**: A grouping label for products, scoped to a merchant. Key attributes: category name (unique within merchant), description (optional).
- **Bulk Operation Result**: The response envelope for a bulk call. Contains three lists: created (with assigned identifier), skipped (duplicate, with existing identifier), failed (with validation reason).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An AI agent can create a 50-item product catalog for a new merchant in a single operation completing in under 5 seconds.
- **SC-002**: Catalog onboarding using bulk operations requires no more than 3 total calls (one for categories, one for products, one for verification), compared to 50+ calls with single-item endpoints.
- **SC-003**: All successfully created items from a bulk operation are accessible to subsequent read operations within the same session.
- **SC-004**: Failed items in a bulk response include actionable error reasons that allow an AI agent to correct and retry only the failed subset without re-submitting the entire batch.
- **SC-005**: Bulk operations with no errors complete with the same reliability rate as equivalent individual creates.

## Assumptions

- Products already have a single-item create endpoint; the bulk variant is additive and does not replace it.
- Categories already have a single-item create endpoint; the bulk variant is additive.
- Partial-success semantics are preferred over all-or-nothing atomicity, since catalog imports typically have a small number of bad rows that should not block the rest.
- A maximum batch size of 200 items is a safe default for catalog-sized operations; this can be raised later if larger imports are needed.
- The caller (AI agent) is already authenticated and authorized for the merchant; authorization failures for the entire request (bad merchant ID, no permission) result in a full rejection, not a partial response.
- Category creation during a bulk products call is out of scope — categories must exist before referencing them in product records.
- The `categories_bulk_create` operation is scoped to merchant (not branch), consistent with how categories are shared across branches within a merchant.
