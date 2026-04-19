# Research: Merchant Onboarding — Product Catalog & Multi-tenancy

**Feature Branch**: `001-merchant-onboarding`
**Date**: 2026-04-19
**Scope**: Discriminator-based multi-tenancy for the product catalog, following Thomas Vitalle's Spring Boot community patterns

---

## Decision 1 — Multi-tenancy Strategy

**Decision**: Discriminator-based (shared schema) multi-tenancy using `merchant_application_id` as the tenant column on all product tables.

**Rationale**: The merchant is already modelled as `MerchantApplication` with a UUID PK. All product rows belong to exactly one merchant. A dedicated column per table (shared schema) avoids connection-pool complexity of schema-per-tenant and is the approach recommended by Thomas Vitalle for single-database SaaS workloads.

**Alternatives considered**:
- Schema-per-tenant: rejected — adds connection-pool overhead and Flyway/Liquibase schema-management complexity with no benefit at current scale.
- Database-per-tenant: rejected — overkill, changes infrastructure model entirely.

**Reference**: Thomas Vitalle, "Multitenant Mystery: Only Rockers in the Building" (Spring I/O 2023). GitHub: `ThomasVitale/spring-boot-multitenancy`.

---

## Decision 2 — Application-level Tenant Filtering (Primary Layer)

**Decision**: Use Hibernate `@FilterDef` / `@Filter` on the `MerchantProduct` entity. Before any product query the service layer enables the Hibernate filter, injecting the current merchant UUID as parameter.

**Rationale**: Vitalle's approach — filters are applied transparently to all queries on the annotated entity without requiring custom JPQL WHERE clauses in every repository method. Hibernate 6 (used via Spring Boot 4) supports `@FilterDef` with `@ParamDef` typed against `UUIDJavaType`.

**Pattern**:
```kotlin
@FilterDef(
    name = "tenantFilter",
    parameters = [ParamDef(name = "merchantId", type = UUIDJavaType::class)]
)
@Filter(name = "tenantFilter", condition = "merchant_application_id = :merchantId")
@Entity
@Table(name = "merchant_products")
class MerchantProduct(...)
```

Service layer enables the filter before each repository call:
```kotlin
val session = entityManager.unwrap(Session::class.java)
session.enableFilter("tenantFilter").setParameter("merchantId", merchantId)
```

**Alternatives considered**:
- `@TenantId` (Hibernate 6 native): rejected — requires `MultiTenantConnectionProvider` wiring; heavier than filter-based approach for discriminator strategy.
- Manual `findByMerchantApplicationId(...)` query parameters: rejected — error-prone; filter approach is less likely to miss tenant scoping on new repository methods.

---

## Decision 3 — PostgreSQL Row Level Security (Safety Net Layer)

**Decision**: Enable PostgreSQL RLS on `merchant_products` and `merchant_product_categories` (if added). Policy reads the `app.current_merchant_id` session variable set by the application at transaction start.

**Rationale**: RLS acts as a defence-in-depth layer. Even if a bug bypasses Hibernate filters, the database rejects cross-tenant reads. This satisfies FR-036 (RLS as safety net).

**Policy**:
```sql
ALTER TABLE blitzpay.merchant_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE blitzpay.merchant_products FORCE ROW LEVEL SECURITY;

CREATE POLICY merchant_tenant_isolation
    ON blitzpay.merchant_products
    USING (
        merchant_application_id = NULLIF(current_setting('app.current_merchant_id', true), '')::uuid
    );
```

The application sets the session variable per transaction:
```kotlin
entityManager.createNativeQuery("SET LOCAL app.current_merchant_id = :mid")
    .setParameter("mid", merchantId.toString())
    .executeUpdate()
```

`NULLIF(..., '')` safely degrades when no tenant context is set (e.g., admin bypass queries running as superuser bypass RLS).

**Alternatives considered**:
- RLS using `current_user`-based policies: rejected — requires separate DB roles per tenant, too complex for current setup.
- No RLS: rejected — FR-036 explicitly requires it as safety net.

---

## Decision 4 — Tenant Context Propagation in Spring WebFlux

**Decision**: Use a `MerchantTenantContext` stored in Project Reactor's `ContextView`. A `WebFilter` extracts the merchant ID from the authenticated principal (request path variable `{merchantId}` validated against the principal) and writes it into the Reactor context. Service layer reads it via `ReactiveSecurityContextHolder`-style pattern.

**Rationale**: WebFlux is non-blocking; thread-locals do not propagate across async chains. Reactor context is the correct propagation mechanism, as documented in Thomas Vitalle's "Cloud Native Spring in Action" and Spring Security's own reactive context holder pattern.

**Flow**:
```
WebFilter → contextWrite { ctx -> ctx.put(MERCHANT_TENANT_KEY, merchantId) }
  └─ Service.flatMap { ctx ->
       val merchantId = ctx.get<UUID>(MERCHANT_TENANT_KEY)
       Mono.fromCallable {
           enableHibernateFilter(merchantId)
           setSessionVariable(merchantId)
           repository.findByIdAndActive(productId)
       }.subscribeOn(Schedulers.boundedElastic())
     }
```

**Alternatives considered**:
- `ThreadLocal`-based `TenantContextHolder`: rejected for WebFlux — thread affinity not guaranteed across reactive operators.
- Passing `merchantId` as explicit method parameter: acceptable fallback but adds boilerplate and is prone to omission.

---

## Decision 5 — Image Storage URL Pattern

**Decision**: Images are stored in S3-compatible object storage (already used for merchant logos via `logo_storage_key` on `merchant_applications`). `MerchantProduct.imageUrl` stores the resolved HTTPS URL (not the raw storage key) since products are buyer-facing.

**Rationale**: The existing `BusinessProfile.logoStorageKey` pattern stores the raw S3 key. For products the URL is more useful for buyer-facing API consumers. The distinction follows the same pattern used for merchant logo: upload is a separate concern from the product record.

**Alternatives considered**:
- Store raw S3 key + resolve URL on read: rejected — adds complexity for no benefit in this context; products are read-heavy and buyer-facing.
- Store base64 binary: rejected (FR-032).

---

## Decision 6 — MerchantLocation: Separate Entity vs Embedded Columns

**Decision**: `MerchantLocation` is a separate JPA entity with its own table (`blitzpay.merchant_locations`), linked 1-to-1 with `MerchantApplication` via a UNIQUE FK column.

**Rationale**: Location is optional (not all merchants have a physical location). Storing nullable `latitude`/`longitude`/`google_place_id` columns directly on `merchant_applications` pollutes the application table with columns that are null for most records and conceptually belong to a different concern (physical presence vs. legal registration). A separate optional entity is cleaner, easier to extend (e.g., adding `altitude`, enrichment metadata), and aligns with the clarification decision.

**Coordinates precision**: `DECIMAL(9,6)` — 9 total digits, 6 decimal places. This gives ~0.11 m resolution globally (GPS accuracy ±0.1 m), and is the de-facto standard for geospatial coordinates in relational databases. Values must satisfy: `-90 ≤ latitude ≤ 90`, `-180 ≤ longitude ≤ 180`.

**Co-requirement rule**: Latitude and longitude are either both provided or both null. A request that supplies only one MUST be rejected with HTTP 400.

**Google Place ID**: Stored as-is (`VARCHAR(255)`) without real-time validation. The standard Google Place ID format is alphanumeric + underscores, max ~100 chars in practice, but 255 provides headroom. Future enrichment (resolving address, reviews) deferred to a background job.

**Endpoint**: `PUT /v1/merchants/{merchantId}/location` performs an upsert — creates the record on first call, replaces on subsequent calls. Accepts all-null body to clear the location. Returns `201 Created` (first write) or `200 OK` (update). Also adds `GET /v1/merchants/{merchantId}/location` to retrieve the location record.

**Alternatives considered**:
- Embedded nullable columns on `merchant_applications`: rejected — pollutes the main table; harder to null-check as a unit; harder to extend.
- Point/geography PostgreSQL type: rejected — requires PostGIS extension which is not currently installed; `DECIMAL(9,6)` pairs are sufficient for the storage/retrieval use case. PostGIS can be added later when spatial queries are needed.

---

## Resolved NEEDS CLARIFICATION Items

| Item | Resolution |
|------|------------|
| Multi-tenancy enforcement mechanism | Hibernate `@Filter` (primary) + PostgreSQL RLS (safety net) |
| Tenant identifier | `MerchantApplication.id` (UUID) = `merchant_application_id` on product tables |
| Reactive context propagation | Reactor `ContextView` via `WebFilter` + `contextWrite` |
| Image storage | S3-compatible object storage; product record stores HTTPS URL |
| Zero-price products | Allowed — `unitPrice DECIMAL(12,4) CHECK (unit_price >= 0)` |
| Currency | Implicit merchant currency — no per-product currency column |
| MerchantLocation storage | Separate entity/table; `DECIMAL(9,6)` lat/lon; `VARCHAR(255)` Place ID; no Maps API call at save |
| Coordinates validation | `-90 ≤ lat ≤ 90`, `-180 ≤ lon ≤ 180`; both-or-neither co-requirement |
| Place ID validation | Deferred to background job; stored as-is |
