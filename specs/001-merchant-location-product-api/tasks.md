# Implementation Tasks: MCP Server - Merchant Location & Product API

**Target Branch**: `001-mcp-server`  
**Module Path**: `src/main/kotlin/com/elegant/software/blitzpay/mcpserver`  
**Module Package**: `com.elegant.software.blitzpay.mcpserver`

## Implementation Phases

### Phase 1: Foundation (Data Model & Persistence)
Core entities, JPA repositories, and DB migrations.

### Phase 2: Business Logic & Services
Domain services, event publishers, business rule enforcement.

### Phase 3: API Controllers & DTOs
REST endpoints, request/response mappers, OpenAPI generation.

### Phase 4: Contracts & Integration Tests
Contract tests with WebTestClient, idempotency, security filters.

### Phase 5: Documentation & Cleanup
README, audit logging verification, module metadata, final integration.

---

## Tasks

### T001: Create MerchantLocation and Product JPA Entities

**Complexity**: Medium  
**Phase**: 1  
**Status**: pending  
**Description**:
Create JPA entity classes for `MerchantLocation` and `Product` under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/persistence/entity/`.

**Entities**:
- **MerchantLocation**:
  - Fields: `id` (UUID, PK), `branchId` (UUID, FK), `name`, `addressLine1`, `addressLine2`, `city`, `postalCode`, `country`, `phone`, `latitude` (Double), `longitude` (Double), `googlePlaceId`, `active` (Boolean, default true), `createdAt` (Instant), `updatedAt` (Instant), `version` (Long, for optimistic locking)
  - Indexes: `idx_location_branch_id` on `branchId`, `idx_location_created_at` on `createdAt`
  - Constraints: Non-null branchId, name; latitude -90..90, longitude -180..180

- **Product**:
  - Fields: `id` (UUID, PK), `branchId` (UUID, FK), `locationId` (UUID, FK to MerchantLocation), `name`, `description`, `priceMinor` (Int, >=0), `currency`, `available` (Boolean, default true), `createdAt` (Instant), `updatedAt` (Instant), `version` (Long, for optimistic locking)
  - Indexes: `idx_product_location_id` on `locationId`, `idx_product_branch_id` on `branchId`
  - Constraints: Non-null branchId, locationId, name, priceMinor, currency

**Deliverables**:
- `MerchantLocationEntity.kt` with `@Entity`, `@Table("merchant_locations")`, JPA annotations
- `ProductEntity.kt` with `@Entity`, `@Table("products")`, JPA annotations
- Proper use of `@Version` for optimistic locking on both entities

**Commit Prefix**: `feat(mcpserver): Create MerchantLocation and Product JPA entities`

---

### T002: Create Liquibase Migration for MerchantLocation & Product Tables

**Complexity**: Small  
**Phase**: 1  
**Status**: pending  
**Depends On**: T001  
**Description**:
Create a Liquibase changelog under `src/main/resources/db/changelog/mcpserver/` to initialize the `merchant_locations` and `products` tables with proper columns, indexes, constraints, and foreign key references to existing `merchant_branches.id`.

**Deliverables**:
- `db/changelog/mcpserver/001-initial-schema.yaml` with tables: `merchant_locations`, `products`, and all required columns/indexes
- Ensure migration integrates with existing project liquibase configuration
- Add changelog reference to main `db/changelog/db.changelog-master.yaml` or primary changelog entry

**Commit Prefix**: `chore(mcpserver): Add Liquibase migration for merchant_locations and products tables`

---

### T003: Create MerchantLocationRepository & ProductRepository (Spring Data JPA)

**Complexity**: Small  
**Phase**: 1  
**Status**: pending  
**Depends On**: T001  
**Description**:
Implement Spring Data JPA repositories under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/persistence/repository/` for efficient querying.

**Repository Methods**:
- **MerchantLocationRepository**:
  - `findById(id: UUID): Optional<MerchantLocationEntity>`
  - `findByIdAndBranchId(id: UUID, branchId: UUID): Optional<MerchantLocationEntity>`
  - `findAllByBranchId(branchId: UUID): List<MerchantLocationEntity>` (with pagination support)
  - `findByIdempotencyKey(key: String): Optional<MerchantLocationEntity>` (for idempotency deduplication, if persisted)

- **ProductRepository**:
  - `findById(id: UUID): Optional<ProductEntity>`
  - `findByIdAndLocationId(id: UUID, locationId: UUID): Optional<ProductEntity>`
  - `findAllByLocationId(locationId: UUID): List<ProductEntity>` (with pagination support)
  - `findByIdempotencyKey(key: String): Optional<ProductEntity>` (for idempotency deduplication, if persisted)

**Deliverables**:
- `MerchantLocationRepository.kt` interface extending `JpaRepository<MerchantLocationEntity, UUID>`
- `ProductRepository.kt` interface extending `JpaRepository<ProductEntity, UUID>`
- Custom query methods for branch/location lookups and idempotency checking

**Commit Prefix**: `feat(mcpserver): Create MerchantLocationRepository and ProductRepository`

---

### T004: Create LocationCreated, LocationUpdated, ProductUpdated Domain Events

**Complexity**: Small  
**Phase**: 2  
**Status**: pending  
**Depends On**: T001  
**Description**:
Define domain event classes under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/domain/event/` for event sourcing and audit trails.

**Events**:
- **LocationCreatedEvent**: fields: `locationId`, `branchId`, `name`, `createdAt`, `createdBy` (userId/agentId)
- **LocationUpdatedEvent**: fields: `locationId`, `branchId`, `previousVersion`, `newVersion`, `updatedAt`, `updatedBy`
- **ProductUpdatedEvent**: fields: `productId`, `locationId`, `branchId`, `previousVersion`, `newVersion`, `updatedAt`, `updatedBy`

Events should extend or implement an `ApplicationEvent` interface or use Spring's `ApplicationEvent`.

**Deliverables**:
- `LocationCreatedEvent.kt`, `LocationUpdatedEvent.kt`, `ProductUpdatedEvent.kt`
- Event classes with immutable fields, proper serialization support for persistence/messaging

**Commit Prefix**: `feat(mcpserver): Create domain events for location and product lifecycle`

---

### T005: Implement MerchantLocationService (Create & Update Logic)

**Complexity**: Medium  
**Phase**: 2  
**Status**: pending  
**Depends On**: T003, T004  
**Description**:
Create domain service under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/domain/service/` implementing business logic for location creation and updates with idempotency, optimistic locking, event publishing, and branch ownership validation.

**Methods**:
- `createLocation(branchId: UUID, request: CreateLocationCommand): MerchantLocationEntity` - validates branch exists, publishes `LocationCreatedEvent`
- `updateLocation(branchId: UUID, locationId: UUID, request: UpdateLocationCommand, expectedVersion: Long?): MerchantLocationEntity` - partial update with optimistic locking, publishes `LocationUpdatedEvent`
- `getLocation(branchId: UUID, locationId: UUID): MerchantLocationEntity` - enforces tenant/branch ownership

**Business Rules**:
- Validate branchId exists and belongs to requesting tenant (injected via SecurityContext or request context)
- Support idempotent creates via Idempotency-Key (tracked in separate table or cache)
- Validate geolocation bounds (latitude -90..90, longitude -180..180)
- Enforce optimistic locking: if `expectedVersion` provided and does not match `entity.version`, throw `OptimisticLockingException`
- Publish events on successful create/update for audit and downstream processing

**Dependencies**:
- MerchantLocationRepository
- EventPublisher (Spring ApplicationEventPublisher)
- MerchantBranchGateway (to validate branch exists)
- IdempotencyStore (optional, for deduplication)

**Deliverables**:
- `MerchantLocationService.kt` with transactional methods
- `CreateLocationCommand.kt` DTO
- `UpdateLocationCommand.kt` DTO
- `MerchantLocationDomainException` and subclasses (e.g., `LocationNotFoundException`, `OptimisticLockingException`)

**Commit Prefix**: `feat(mcpserver): Implement MerchantLocationService with idempotency and optimistic locking`

---

### T006: Implement ProductService (Create & Update Logic)

**Complexity**: Medium  
**Phase**: 2  
**Status**: pending  
**Depends On**: T003, T004, T005  
**Description**:
Create domain service under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/domain/service/` for product creation and updates with idempotency, optimistic locking, event publishing, and location ownership validation.

**Methods**:
- `createProduct(branchId: UUID, locationId: UUID, request: CreateProductCommand): ProductEntity` - validates location exists and is active, publishes event
- `updateProduct(branchId: UUID, productId: UUID, request: UpdateProductCommand, expectedVersion: Long?): ProductEntity` - partial update with optimistic locking, publishes `ProductUpdatedEvent`
- `getProduct(branchId: UUID, productId: UUID): ProductEntity` - enforces tenant/branch ownership
- Internal: `verifyLocationOwnership(branchId: UUID, locationId: UUID): MerchantLocationEntity`

**Business Rules**:
- Validate branchId matches requesting tenant
- Validate locationId exists and belongs to branchId
- Validate location is active (unless business rule allows inactive)
- Support idempotent creates via Idempotency-Key
- Enforce optimistic locking with version checking
- Publish `ProductUpdatedEvent` on successful updates

**Dependencies**:
- ProductRepository
- MerchantLocationService (to verify location ownership and active status)
- EventPublisher
- MerchantBranchGateway

**Deliverables**:
- `ProductService.kt` with transactional methods
- `CreateProductCommand.kt` DTO
- `UpdateProductCommand.kt` DTO
- `ProductDomainException` and subclasses (e.g., `ProductNotFoundException`, `InactiveLocationException`)

**Commit Prefix**: `feat(mcpserver): Implement ProductService with idempotency and optimistic locking`

---

### T007: Create DTOs & Request/Response Mappers

**Complexity**: Small  
**Phase**: 3  
**Status**: pending  
**Depends On**: T005, T006  
**Description**:
Define API-facing DTOs and mappers under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/dto/` to bridge REST payloads and domain entities.

**DTOs**:
- **LocationCreateRequest**: name, address (object with street, city, postcode, country), phone, latitude, longitude, googlePlaceId
- **LocationPatchRequest**: optional name, address, phone, latitude, longitude, version (for optimistic locking)
- **LocationResponse**: id, branchId, name, address, phone, latitude, longitude, active, createdAt, updatedAt, version
- **Address** (nested DTO): street, city, postcode, country

- **ProductCreateRequest**: name, description, priceMinor, currency, available
- **ProductPatchRequest**: optional name, description, priceMinor, currency, available, version
- **ProductResponse**: id, branchId, locationId, name, description, priceMinor, currency, available, createdAt, updatedAt, version

**Mappers**:
- `LocationMapper`: `toResponse(entity: MerchantLocationEntity): LocationResponse`
- `LocationMapper`: `toDomain(request: LocationCreateRequest, branchId: UUID): MerchantLocationEntity`
- `ProductMapper`: `toResponse(entity: ProductEntity): ProductResponse`
- `ProductMapper`: `toDomain(request: ProductCreateRequest, branchId: UUID, locationId: UUID): ProductEntity`

**Deliverables**:
- DTOs with `@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)` or appropriate Jackson annotations
- `LocationMapper.kt`, `ProductMapper.kt` with Spring stereotypes (e.g., `@Component`) or manual mapping functions
- Proper validation annotations (`@NotBlank`, `@NotNull`, `@Min`, etc.) on DTOs

**Commit Prefix**: `feat(mcpserver): Create DTOs and request/response mappers for Location and Product APIs`

---

### T008: Implement MerchantLocationController

**Complexity**: Medium  
**Phase**: 3  
**Status**: pending  
**Depends On**: T005, T007  
**Description**:
Create REST controller under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/controller/` to expose location creation and update endpoints.

**Endpoints**:
- **POST /v1/branches/{branchId}/locations** (Create Location)
  - Consumes: `LocationCreateRequest` with `Idempotency-Key` header (optional)
  - Returns: 201 with `LocationResponse`
  - Error responses: 400 (validation), 404 (branch not found), 409 (business conflict)

- **PATCH /v1/branches/{branchId}/locations/{locationId}** (Update Location)
  - Consumes: `LocationPatchRequest` with optional `version` field or `If-Match` header
  - Returns: 200 with `LocationResponse`
  - Error responses: 400 (validation), 404 (not found), 412 (version mismatch), 409 (business conflict)

**Features**:
- Extract tenant/agent identity from JWT principal (SecurityContext)
- Enforce tenant/branch ownership (403 if not owner)
- Support Idempotency-Key for idempotent create (deduplicate via service)
- Support version-based optimistic locking (If-Match header or version field in PATCH body)
- Handle and map domain exceptions to appropriate HTTP status codes
- Generate OpenAPI/Swagger documentation via `@Operation`, `@Schema`, `@ApiResponse` annotations

**Dependencies**:
- MerchantLocationService
- LocationMapper
- SecurityContext (for tenant/agent identity)

**Deliverables**:
- `MerchantLocationController.kt` with `@RestController`, `@RequestMapping("/v1/branches")`, `@CrossOrigin` if needed
- Exception handlers for domain exceptions (map to 404, 409, 412 as appropriate)
- Swagger annotations for API documentation

**Commit Prefix**: `feat(mcpserver): Implement MerchantLocationController with CRUD endpoints`

---

### T009: Implement ProductController

**Complexity**: Medium  
**Phase**: 3  
**Status**: pending  
**Depends On**: T006, T007  
**Description**:
Create REST controller under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/controller/` to expose product creation and update endpoints.

**Endpoints**:
- **POST /v1/branches/{branchId}/locations/{locationId}/products** (Create Product)
  - Consumes: `ProductCreateRequest` with `Idempotency-Key` header (optional)
  - Returns: 201 with `ProductResponse`
  - Error responses: 400 (validation), 404 (location not found), 409 (business conflict)

- **PATCH /v1/branches/{branchId}/products/{productId}** (Update Product)
  - Consumes: `ProductPatchRequest` with optional `version` field or `If-Match` header
  - Returns: 200 with `ProductResponse`
  - Error responses: 400 (validation), 404 (not found), 412 (version mismatch), 409 (business conflict)

**Features**:
- Extract tenant/agent identity from JWT principal
- Enforce tenant/branch ownership
- Support Idempotency-Key for idempotent create
- Support version-based optimistic locking (If-Match or version field)
- Handle and map domain exceptions to appropriate HTTP status codes
- Generate OpenAPI/Swagger documentation

**Dependencies**:
- ProductService
- ProductMapper
- SecurityContext

**Deliverables**:
- `ProductController.kt` with `@RestController`, `@RequestMapping("/v1/branches")`, `@CrossOrigin` if needed
- Exception handlers for domain exceptions
- Swagger annotations for API documentation

**Commit Prefix**: `feat(mcpserver): Implement ProductController with CRUD endpoints`

---

### T010: Implement JWT Tenant Enforcement Filter

**Complexity**: Medium  
**Phase**: 3  
**Status**: pending  
**Depends On**: T008, T009  
**Description**:
Create a servlet filter or Spring Security filter under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/config/security/` to enforce tenant isolation and branch ownership from JWT claims.

**Filter Logic**:
- Extract JWT from Authorization header
- Parse JWT and validate signature (assume existing token provider)
- Extract `tenant_id`, `branch_id` (or equivalent) claims
- Store in `SecurityContext` or thread-local context for controller access
- Reject requests without valid tenant claim (401 Unauthorized)
- Reject requests attempting to access resources outside their tenant scope (403 Forbidden)

**Configuration**:
- Register filter with Spring Security chain or servlet filter chain
- Apply filter selectively to `/v1/branches/**` paths
- Ensure filter runs before controller layer

**Dependencies**:
- Existing JWT token provider or Spring Security configuration
- SecurityContext utilities

**Deliverables**:
- `JwtTenantEnforcementFilter.kt` extending `OncePerRequestFilter` or implementing `Filter`
- `SecurityContextConfig.kt` or update to existing security config to register filter
- Tests to verify tenant isolation is enforced

**Commit Prefix**: `feat(mcpserver): Implement JWT tenant enforcement filter`

---

### T011: Implement Idempotency Support (Idempotency-Key & ETag)

**Complexity**: Medium  
**Phase**: 4  
**Status**: pending  
**Depends On**: T005, T006, T008, T009  
**Description**:
Implement server-side idempotency deduplication for create endpoints and ETag/version support for update endpoints under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/domain/idempotency/`.

**Components**:
- **IdempotencyStore**: Interface for storing request fingerprints and responses (in-memory cache, Redis, or DB table)
- **IdempotencyInterceptor**: Spring aspect or filter to intercept POST requests, check `Idempotency-Key` header, deduplicate if duplicate detected
- **ETagGenerator**: Utility to generate ETags from entity version or timestamp
- **If-Match Validator**: Check If-Match header against current entity version, throw 412 if mismatch

**Idempotency Logic**:
1. On POST (create): Extract `Idempotency-Key` header
2. Query IdempotencyStore for existing response with same key
3. If found: return cached response without re-executing service
4. If not found: execute service, store result with key, return response

**ETag/If-Match Logic**:
1. On PATCH (update): Extract `If-Match` header or `version` field from request body
2. Compare with entity.version
3. If mismatch: throw `OptimisticLockingException` → 412 Precondition Failed
4. If match: proceed with update

**Deliverables**:
- `IdempotencyStore.kt` interface with `getResponse(key: String)`, `storeResponse(key: String, response: Any)`
- `IdempotencyInterceptor.kt` aspect or filter to handle idempotency logic
- `ETagGenerator.kt` utility function
- Optional: `IdempotencyKeyExtractor.kt` to extract and validate Idempotency-Key header format (UUID or similar)

**Commit Prefix**: `feat(mcpserver): Implement idempotency and ETag support for API endpoints`

---

### T012: Implement Event Publisher & Audit Logging

**Complexity**: Medium  
**Phase**: 4  
**Status**: pending  
**Depends On**: T004, T005, T006  
**Description**:
Create event publishing infrastructure under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/domain/event/` to emit domain events for audit trails and downstream processing.

**Components**:
- **EventPublisher** (or use Spring's `ApplicationEventPublisher`): Emit domain events to listeners
- **AuditEventListener**: Listen to `LocationCreatedEvent`, `LocationUpdatedEvent`, `ProductUpdatedEvent` and log audit records to a database table or external audit service
- **AuditLog** entity: Capture event type, entity id, tenant_id, agent_id, timestamp, changes (JSON diff), status

**Event Emission**:
- Publish events from MerchantLocationService and ProductService after successful persistence
- Include tenant_id, agent_id (from SecurityContext), timestamp, entity id, version in event payload

**Audit Logging**:
- Listener captures events and logs to `audit_logs` table or external service
- Log should include: event_type, entity_type, entity_id, tenant_id, agent_id, created_at, action (CREATE/UPDATE/DELETE), changes (JSON diff)

**Deliverables**:
- Update `LocationCreatedEvent`, `LocationUpdatedEvent`, `ProductUpdatedEvent` to include audit fields
- `AuditEventListener.kt` with `@EventListener` method
- Optional `AuditLog.kt` entity and `AuditLogRepository.kt` if persisting to DB
- Integration with existing monitoring/logging framework (e.g., SLF4J, structured logging)

**Commit Prefix**: `feat(mcpserver): Implement event publishing and audit logging`

---

### T013: Create Contract Tests (WebTestClient) for MerchantLocationController

**Complexity**: Large  
**Phase**: 4  
**Status**: pending  
**Depends On**: T008, T010, T011  
**Description**:
Create comprehensive contract tests under `src/contractTest/kotlin/com/elegant/software/blitzpay/mcpserver/` using WebTestClient to verify location endpoints against OpenAPI contract.

**Test Cases**:
- **Happy Path (201 Created)**:
  - POST /v1/branches/{branchId}/locations with valid payload → 201 with LocationResponse
  - Idempotent retry with same Idempotency-Key → 201 (or 200 on retry, consistent with spec)
  - Verify Location persisted with correct fields, createdAt, updatedAt, version=0

- **Validation Errors (400 Bad Request)**:
  - Missing required fields (name, address)
  - Invalid latitude/longitude
  - Malformed JSON

- **Not Found (404)**:
  - Non-existent branchId → 404 with error message

- **Conflicts (409)**:
  - Duplicate create with different Idempotency-Key (not idempotent) → 409 or different handling

- **Authorization (403)**:
  - Missing JWT token → 401 Unauthorized
  - JWT with different tenant_id → 403 Forbidden

- **Update Endpoint (PATCH)**:
  - PATCH /v1/branches/{branchId}/locations/{locationId} with partial update → 200 with updated response
  - Version mismatch (If-Match or version field) → 412 Precondition Failed
  - Update immutable field (branchId) → 400 or ignored

**Test Structure**:
- Extend `ContractVerifierBase` (existing test base class)
- Mock MerchantLocationService or use real service with test data
- Use test fixtures for branch, location, JWT token
- Assert response status, body structure, headers (ETag, etc.)

**Deliverables**:
- `MerchantLocationControllerContractTest.kt` with all test cases above
- Test fixtures/builders for LocationCreateRequest, LocationResponse, JWT tokens
- Assertions aligned with OpenAPI schema

**Commit Prefix**: `test(mcpserver): Add contract tests for MerchantLocationController`

---

### T014: Create Contract Tests (WebTestClient) for ProductController

**Complexity**: Large  
**Phase**: 4  
**Status**: pending  
**Depends On**: T009, T010, T011, T013  
**Description**:
Create comprehensive contract tests under `src/contractTest/kotlin/com/elegant/software/blitzpay/mcpserver/` using WebTestClient to verify product endpoints against OpenAPI contract.

**Test Cases**:
- **Happy Path (201 Created)**:
  - POST /v1/branches/{branchId}/locations/{locationId}/products with valid payload → 201 with ProductResponse
  - Idempotent retry with same Idempotency-Key → consistent behavior
  - Verify Product persisted with correct fields, createdAt, updatedAt, version=0
  - branchId derived from locationId and persisted correctly

- **Validation Errors (400)**:
  - Missing required fields (name, priceMinor, currency)
  - Invalid priceMinor (negative)
  - Malformed currency code

- **Not Found (404)**:
  - Non-existent locationId → 404
  - Non-existent productId (on update) → 404

- **Conflicts (409)**:
  - Product creation when location is inactive → 409 or per business rule

- **Authorization (403)**:
  - Missing JWT token → 401
  - JWT with different tenant_id → 403

- **Update Endpoint (PATCH)**:
  - PATCH /v1/branches/{branchId}/products/{productId} with partial update → 200
  - Version mismatch → 412 Precondition Failed
  - Immutable fields (branchId, locationId) → 400 or ignored

**Test Structure**:
- Extend `ContractVerifierBase`
- Mock or use real services with test data
- Create test locations and products in setup
- Assert response alignment with OpenAPI schema

**Deliverables**:
- `ProductControllerContractTest.kt` with all test cases above
- Test fixtures for ProductCreateRequest, ProductResponse, test locations
- Assertions aligned with OpenAPI schema

**Commit Prefix**: `test(mcpserver): Add contract tests for ProductController`

---

### T015: Create Unit Tests for MerchantLocationService

**Complexity**: Medium  
**Phase**: 4  
**Status**: pending  
**Depends On**: T005, T011  
**Description**:
Create unit tests under `src/test/kotlin/com/elegant/software/blitzpay/mcpserver/domain/service/` to verify business logic of MerchantLocationService.

**Test Cases**:
- **Create Location**:
  - Valid create returns entity with id, createdAt, updatedAt, version=0
  - Creates with same Idempotency-Key returns same entity (idempotency)
  - LocationCreatedEvent published
  - Branch not found → LocationNotFoundException
  - Invalid coordinates (latitude/longitude out of range) → ValidationException

- **Update Location**:
  - Partial update succeeds → entity.version incremented, updatedAt changed
  - Version mismatch (expectedVersion != entity.version) → OptimisticLockingException (412)
  - LocationUpdatedEvent published with previousVersion, newVersion
  - Cannot update branchId (immutable) → ValidationException or silently ignored
  - Location not found → LocationNotFoundException

- **Get Location**:
  - Returns entity for valid id + branchId
  - 404 if not found or branchId mismatch

**Test Structure**:
- Use Mockito for MerchantLocationRepository, MerchantBranchGateway, EventPublisher mocks
- Use test builders for entities and requests
- Assert event emissions with ArgumentCaptor
- Assert exception types and messages

**Deliverables**:
- `MerchantLocationServiceTest.kt` with all test cases
- Test fixtures: `MerchantLocationTestBuilder.kt`, `CreateLocationCommandTestBuilder.kt`
- Assertions for service state changes and event emissions

**Commit Prefix**: `test(mcpserver): Add unit tests for MerchantLocationService`

---

### T016: Create Unit Tests for ProductService

**Complexity**: Medium  
**Phase**: 4  
**Status**: pending  
**Depends On**: T006, T011, T015  
**Description**:
Create unit tests under `src/test/kotlin/com/elegant/software/blitzpay/mcpserver/domain/service/` to verify business logic of ProductService.

**Test Cases**:
- **Create Product**:
  - Valid create returns entity with id, createdAt, updatedAt, version=0
  - Creates with same Idempotency-Key returns same entity (idempotency)
  - ProductUpdatedEvent published (or ProductCreatedEvent if separate)
  - Location not found → ProductNotFoundException
  - Location inactive → InactiveLocationException (409)
  - Invalid priceMinor (negative) → ValidationException

- **Update Product**:
  - Partial update succeeds → entity.version incremented, updatedAt changed
  - Version mismatch → OptimisticLockingException (412)
  - ProductUpdatedEvent published
  - Cannot update branchId/locationId (immutable)
  - Product not found → ProductNotFoundException

- **Get Product**:
  - Returns entity for valid id + branchId
  - 404 if not found or branchId mismatch

- **Location Ownership Verification**:
  - Fails if locationId doesn't exist
  - Fails if locationId belongs to different branchId

**Test Structure**:
- Mock ProductRepository, MerchantLocationService, EventPublisher
- Use test builders for entities and requests
- Assert event emissions and state transitions
- Verify location ownership validation

**Deliverables**:
- `ProductServiceTest.kt` with all test cases
- Test fixtures: `ProductTestBuilder.kt`, `CreateProductCommandTestBuilder.kt`
- Assertions for service behavior and event emissions

**Commit Prefix**: `test(mcpserver): Add unit tests for ProductService`

---

### T017: Configure OpenAPI Generation (springdoc-openapi)

**Complexity**: Small  
**Phase**: 3  
**Status**: pending  
**Depends On**: T008, T009  
**Description**:
Configure springdoc-openapi-starter-webmvc-ui under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/config/` to auto-generate OpenAPI documentation from controller annotations and validate against provided contract at `specs/001-merchant-location-product-api/contracts/openapi.yml`.

**Configuration**:
- Add `@OpenAPIDefinition` annotation to main `@Configuration` class or application entry point
- Define custom OpenAPI bean with title, version, description, servers
- Configure springdoc to scan mcpserver package
- Disable or filter out non-MCP endpoints from OpenAPI output if needed
- Add `@Tag` annotation to controllers for logical grouping

**Validation**:
- Generate OpenAPI YAML/JSON at application startup or build time
- Compare generated schema against contract to ensure alignment (manual or automated)

**Deliverables**:
- `OpenApiConfig.kt` with `@Configuration` and OpenAPI bean definitions
- Update application.yml/properties with springdoc configuration (e.g., springdoc.api-docs.path, springdoc.swagger-ui.path)
- OpenAPI endpoint at /v1/api-docs or /v1/swagger-ui.html

**Commit Prefix**: `feat(mcpserver): Configure OpenAPI generation with springdoc-openapi`

---

### T018: Update Module Metadata (package-info & @Module Annotation)

**Complexity**: Small  
**Phase**: 5  
**Status**: pending  
**Depends On**: T008, T009  
**Description**:
Create module metadata under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/` and configure Spring Modulith `@Module` annotation to define module boundaries and publish named interfaces.

**Metadata**:
- **package-info.kt**: Define module entry points with `@NamedInterface` annotations
  - MerchantLocationPublicAPI (for controllers)
  - ProductPublicAPI (for controllers)

**Module Annotation**:
- Apply `@Module` annotation to root package or designated module coordinator class
- Define displayName, description
- Declare public API packages (api.*)
- Declare internal packages (domain.*, persistence.*)

**Deliverables**:
- `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/package-info.kt` with `@PackageInfo`, `@NamedInterface`
- Optional `ModuleCoordinator.kt` with `@Module` annotation or update to application-level module definition
- Ensure modulith can verify API boundaries and detect violations

**Commit Prefix**: `chore(mcpserver): Add modulith module metadata and named interfaces`

---

### T019: Add mcpserver Module to Project Modulith Config

**Complexity**: Small  
**Phase**: 5  
**Status**: pending  
**Depends On**: T018  
**Description**:
Register the mcpserver module in the project's modulith configuration (application.yml or modulith configuration bean) to enable module documentation generation and compliance checking.

**Configuration**:
- Update `application.yml` or `ModulithConfiguration.kt` to include modulith package scan or module registry entry for `com.elegant.software.blitzpay.mcpserver`
- Ensure modulith observability and documentation tools can discover the module

**Deliverables**:
- Updated application.yml with `spring.modulith.module-packages` entry or equivalent configuration
- Optional: Modulith documentation/diagram generation configured

**Commit Prefix**: `chore(mcpserver): Register mcpserver module in modulith configuration`

---

### T020: Create/Update README for MCP Server Module

**Complexity**: Small  
**Phase**: 5  
**Status**: pending  
**Depends On**: T008, T009, T017  
**Description**:
Create comprehensive README under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/README.md` or update project README with MCP Server module documentation.

**Content**:
- Module overview: Purpose, scope (merchant location & product APIs)
- Architecture: Module structure (api, domain, persistence), key services
- API Documentation:
  - Endpoint summaries with curl examples (reference quickstart.md)
  - Authentication/Authorization (JWT tenant enforcement)
  - Idempotency & ETag support
  - Error codes and responses
- Data Model:
  - MerchantLocation, Product entities, relationships
  - Indexes, constraints
- Running/Testing:
  - How to run contract tests: `./gradlew contractTest --tests "*mcpserver*"`
  - How to run unit tests: `./gradlew test --tests "*mcpserver*"`
  - Local development setup (if applicable)
- Configuration:
  - Key properties (e.g., idempotency store type, event publisher)
  - Module dependencies (MerchantBranch via GatewayInterface)
- Event Sourcing:
  - Event types, listeners, audit trails

**Deliverables**:
- `specs/001-merchant-location-product-api/README.md` or update main project README
- Examples of API calls (curl, JSON payloads)
- Module architecture diagram (optional)

**Commit Prefix**: `docs(mcpserver): Add comprehensive README for MCP Server module`

---

### T021: Implement Request/Response Validation and Error Handling

**Complexity**: Medium  
**Phase**: 5  
**Status**: pending  
**Depends On**: T008, T009, T007  
**Description**:
Enhance validation and centralized error handling under `src/main/kotlin/com/elegant/software/blitzpay/mcpserver/api/exception/` to provide consistent, informative error responses.

**Components**:
- **Input Validation**: Use `@Valid`, `@NotNull`, `@Min`, `@Max`, `@Pattern` on DTO fields
- **Custom Validators**: Implement `@ValidCoordinates`, `@ValidISOCurrency` etc. for complex validation
- **Global Exception Handler**: `@RestControllerAdvice` to catch and format exceptions:
  - `MethodArgumentNotValidException` → 400 with field-level errors
  - `OptimisticLockingException` → 412 Precondition Failed
  - `LocationNotFoundException`, `ProductNotFoundException` → 404 Not Found
  - `InactiveLocationException` → 409 Conflict
  - `ValidationException` → 400 Bad Request
  - Generic exceptions → 500 Internal Server Error

**Error Response Format**:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [
    {
      "field": "latitude",
      "message": "must be between -90 and 90"
    }
  ],
  "timestamp": "2026-04-21T10:30:00Z"
}
```

**Deliverables**:
- `GlobalExceptionHandler.kt` with `@RestControllerAdvice`
- Custom validator annotations and implementations
- DTO validation constraints
- Error response DTO

**Commit Prefix**: `feat(mcpserver): Implement comprehensive request validation and error handling`

---

### T022: Add Audit Logging & User/Agent Identity Tracking

**Complexity**: Small  
**Phase**: 5  
**Status**: pending  
**Depends On**: T012, T005, T006  
**Description**:
Enhance audit logging to capture user/agent identity and request metadata for compliance and debugging.

**Implementation**:
- Extract agent/user identity from JWT principal (SecurityContext)
- Capture in domain events (LocationCreatedEvent, etc.)
- Log to audit_logs table or external service with:
  - event_type (CREATE_LOCATION, UPDATE_LOCATION, CREATE_PRODUCT, UPDATE_PRODUCT)
  - entity_id, tenant_id, branch_id, agent_id
  - timestamp, action
  - Optional: request metadata (IP, user-agent, idempotency key)
  - Optional: previous/new values (JSON diff for updates)

**Deliverables**:
- Update event classes to include agent_id, tenant_id
- Update AuditEventListener to capture and log all metadata
- Optional: Liquibase migration for audit_logs table if persisting to DB

**Commit Prefix**: `feat(mcpserver): Add audit logging with user/agent identity tracking`

---

### T023: Integration Test: End-to-End Create Location & Product Flow

**Complexity**: Medium  
**Phase**: 4  
**Status**: pending  
**Depends On**: T013, T014  
**Description**:
Create integration test under `src/contractTest/kotlin/com/elegant/software/blitzpay/mcpserver/` to verify end-to-end flow from location creation to product creation within same transaction/session.

**Test Scenario**:
1. Create MerchantLocation via POST /v1/branches/{branchId}/locations → 201
2. Verify location persisted and retrievable
3. Create Product via POST /v1/branches/{branchId}/locations/{locationId}/products → 201
4. Verify product persisted with correct locationId, branchId
5. Update Location via PATCH /v1/branches/{branchId}/locations/{locationId} → 200
6. Update Product via PATCH /v1/branches/{branchId}/products/{productId} → 200
7. Verify final state matches expectations

**Assertions**:
- All entities created with correct UUIDs, timestamps, versions
- Tenant/branch ownership enforced throughout
- Events published for each operation
- Audit logs recorded for all operations

**Deliverables**:
- `MerchantLocationProductIntegrationTest.kt` with end-to-end flow
- Test setup with fixtures (branch, user, JWT token)
- Assertions for data persistence and event emission

**Commit Prefix**: `test(mcpserver): Add end-to-end integration test for location and product lifecycle`

---

## Summary of Artifacts by Task

| Task | Artifacts | Count |
|------|-----------|-------|
| T001 | MerchantLocationEntity.kt, ProductEntity.kt | 2 |
| T002 | 001-initial-schema.yaml (Liquibase migration) | 1 |
| T003 | MerchantLocationRepository.kt, ProductRepository.kt | 2 |
| T004 | LocationCreatedEvent.kt, LocationUpdatedEvent.kt, ProductUpdatedEvent.kt | 3 |
| T005 | MerchantLocationService.kt, CreateLocationCommand.kt, UpdateLocationCommand.kt, exceptions | 4 |
| T006 | ProductService.kt, CreateProductCommand.kt, UpdateProductCommand.kt, exceptions | 4 |
| T007 | DTOs (Location*, Product*, Address), Mappers | 8 |
| T008 | MerchantLocationController.kt | 1 |
| T009 | ProductController.kt | 1 |
| T010 | JwtTenantEnforcementFilter.kt, SecurityContextConfig.kt | 2 |
| T011 | IdempotencyStore.kt, IdempotencyInterceptor.kt, ETagGenerator.kt | 3 |
| T012 | Event Publisher, AuditEventListener.kt, AuditLog.kt (optional) | 2 |
| T013 | MerchantLocationControllerContractTest.kt, test fixtures | 2 |
| T014 | ProductControllerContractTest.kt, test fixtures | 2 |
| T015 | MerchantLocationServiceTest.kt, test builders | 2 |
| T016 | ProductServiceTest.kt, test builders | 2 |
| T017 | OpenApiConfig.kt, application.yml updates | 2 |
| T018 | package-info.kt, ModuleCoordinator.kt (optional) | 1 |
| T019 | application.yml modulith config update | 1 |
| T020 | README.md | 1 |
| T021 | GlobalExceptionHandler.kt, validators, error DTOs | 3 |
| T022 | Audit logging enhancements to T012 | 0 |
| T023 | MerchantLocationProductIntegrationTest.kt | 1 |

**Total Estimated Artifacts**: ~45 files/components

---

## Implementation Order & Dependencies

**Recommended Phases**:

### Phase 1: Data Model & Persistence (Days 1–2)
- T001 → T002 → T003
- Establishes database schema and basic repository layer
- **Deliverable**: Persistent entities and migration

### Phase 2: Business Logic & Events (Days 2–3)
- T004 → T005 → T006
- Implements core services with optimistic locking and event publishing
- **Deliverable**: Transactional business logic with event sourcing

### Phase 3: API & DTOs (Days 3–4)
- T007 → T008 → T009 → T017
- Exposes REST endpoints with OpenAPI documentation
- **Deliverable**: Public REST API aligned with OpenAPI contract

### Phase 4: Security & Idempotency (Days 4–5)
- T010 → T011 → T021
- Enforces tenant isolation and idempotent operations
- **Deliverable**: Secure, resilient API

### Phase 5: Testing & Integration (Days 5–7)
- T013 → T014 → T015 → T016 → T023
- Comprehensive contract and unit tests
- **Deliverable**: Test coverage >80%, all scenarios passing

### Phase 6: Documentation & Cleanup (Days 7–8)
- T012 → T018 → T019 → T020 → T022
- Audit logging, module metadata, documentation
- **Deliverable**: Production-ready module with audit trail and documentation

---

## Dependency Graph

```
T001 (Entities)
  ├── T002 (Migration)
  ├── T003 (Repositories)
  │   └── T005 (LocationService)
  │       ├── T008 (LocationController)
  │       │   └── T013 (Contract Tests)
  │       │       └── T023 (Integration Test)
  │       └── T006 (ProductService)
  │           ├── T009 (ProductController)
  │           │   └── T014 (Contract Tests)
  │           │       └── T023 (Integration Test)
  │           └── T015 (LocationService Tests)
  │               └── T016 (ProductService Tests)
  ├── T004 (Domain Events)
  │   └── T012 (Event Publisher)
  │       └── T022 (Audit Logging)
  ├── T007 (DTOs & Mappers)
  │   └── T008, T009
  ├── T010 (JWT Filter)
  │   └── T008, T009
  ├── T011 (Idempotency)
  │   └── T008, T009
  ├── T017 (OpenAPI Config)
  │   └── T008, T009
  ├── T018 (Module Metadata)
  ├── T019 (Modulith Config)
  └── T020 (README)
  └── T021 (Error Handling)
```

---

## Key Decisions & Patterns

1. **Optimistic Locking**: Use `@Version` on entities, pass `expectedVersion` in PATCH requests or `If-Match` header
2. **Idempotency**: Store Idempotency-Key and response in cache; return cached response on retry
3. **Event-Driven**: Emit domain events after successful persistence for audit and downstream processing
4. **Tenant Isolation**: JWT filter extracts and enforces tenant_id/branch_id throughout request lifecycle
5. **Exception Mapping**: Global exception handler translates domain exceptions to HTTP status codes (404, 409, 412, etc.)
6. **Contract Testing**: Use WebTestClient with OpenAPI spec alignment to ensure API adheres to contract
7. **Module Boundaries**: Use `@NamedInterface` to explicitly publish API entry points; keep domain/persistence internal

---

## Success Criteria

- ✅ All 23 tasks completed
- ✅ Contract tests pass with >95% assertion coverage
- ✅ OpenAPI schema generated and aligned with provided contract
- ✅ Idempotency and optimistic locking implemented and tested
- ✅ JWT tenant enforcement verified (403 on unauthorized branch access)
- ✅ Audit logs captured for all create/update operations
- ✅ End-to-end flow tested (location creation → product creation → updates)
- ✅ Module metadata and boundaries defined
- ✅ README and documentation complete
- ✅ No blocking dependencies; all tasks can be parallelized after Phase 1

