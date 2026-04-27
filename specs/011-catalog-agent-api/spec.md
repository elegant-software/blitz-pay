# Feature Specification: Product Catalog Website Agent API

**Feature Branch**: `011-catalog-agent-api`
**Created**: 2026-04-26
**Status**: Draft
**Input**: User description: "I want to have an async API user will request to create or update product catalog web site based on merchant branches and product information, application should use Kotlin Koog agent framework for this purpose https://docs.koog.ai/"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request Catalog Website Generation (Priority: P1)

A merchant submits a request to generate a product catalog website for their business. The system immediately acknowledges the request with a job reference and begins processing in the background. The merchant can check the job status and, once complete, access the generated catalog website.

**Why this priority**: This is the core end-to-end flow. All other stories depend on this working first. It delivers the primary value: a merchant gets a publicly accessible, up-to-date product catalog website without manual effort.

**Independent Test**: Can be tested by submitting a catalog generation request for a merchant with existing branch and product data, polling for job completion, and verifying a catalog website reflecting that merchant's products is accessible.

**Acceptance Scenarios**:

1. **Given** an authenticated merchant with at least one branch and associated products, **When** they submit a catalog generation request, **Then** the system returns a job ID and a status of `QUEUED` within 1 second.
2. **Given** a submitted catalog generation job, **When** the system finishes processing, **Then** the job status changes to `COMPLETED` and a catalog website URL is available.
3. **Given** a completed catalog job, **When** a user visits the catalog URL, **Then** they see a web page listing the merchant's products organized by branch.

---

### User Story 2 - Update an Existing Catalog Website (Priority: P2)

A merchant whose catalog website already exists requests an update after adding new products, changing prices, or modifying branch information. The system regenerates or refreshes the existing catalog in the background without changing the catalog's public URL.

**Why this priority**: Merchant data changes frequently. Without the ability to update the catalog, the generated website becomes stale and loses its value. Stable URL preservation prevents broken links.

**Independent Test**: Can be tested by first generating a catalog, then changing product data, submitting an update request, waiting for completion, and verifying the catalog website reflects the new data at the same URL.

**Acceptance Scenarios**:

1. **Given** a merchant with an existing catalog website, **When** they submit an update request, **Then** the system returns a new job ID and starts updating the catalog without changing its public URL.
2. **Given** a completed update job, **When** the catalog website is accessed, **Then** it reflects the most recently submitted product and branch data.
3. **Given** a merchant submitting an update while a previous job is still processing, **Then** the system either queues the update to run after the current job or replaces the pending job with the new request.

---

### User Story 3 - Track Catalog Generation Job Status (Priority: P3)

A merchant or an integrated system checks the status of a previously submitted catalog job to know whether it is still processing, has completed, or has failed — and to receive enough information to act on the result.

**Why this priority**: Since generation is asynchronous, status tracking is essential for callers to know when to surface the result to the end user or to trigger retry logic on failure.

**Independent Test**: Can be tested independently by submitting a job, polling the status endpoint at intervals, and verifying transitions through `QUEUED` → `PROCESSING` → `COMPLETED` (or `FAILED`) with appropriate metadata at each stage.

**Acceptance Scenarios**:

1. **Given** a submitted job ID, **When** a status check is performed, **Then** the response includes the current status, submission timestamp, and estimated or actual completion time.
2. **Given** a job in `FAILED` state, **When** a status check is performed, **Then** the response includes a human-readable failure reason so the merchant or support team can take action.
3. **Given** an unknown or expired job ID, **When** a status check is performed, **Then** the system returns a clear not-found response rather than an error.

---

### Edge Cases

- What happens when a merchant has no branches or no products at the time of the request?
- How does the system handle a catalog generation request for a merchant whose data changes mid-job?
- What happens if the agent pipeline fails partway through generation (e.g., after some content is written but before the site is published)?
- How does the system behave when many merchants trigger catalog generation simultaneously?
- What if the merchant's product catalog is very large (hundreds of products, many branches)?
- What if a merchant submits duplicate requests in quick succession?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept an asynchronous request to generate or update a product catalog website for an authenticated merchant.
- **FR-002**: The system MUST return a unique job identifier and initial status immediately upon accepting a request, without waiting for generation to complete.
- **FR-003**: The system MUST automatically populate the catalog using the merchant's existing branch and product data — no manual content entry required by the merchant.
- **FR-004**: The system MUST provide a status inquiry endpoint where callers can retrieve the current state of a catalog job at any time.
- **FR-005**: The system MUST transition jobs through defined states: `QUEUED`, `PROCESSING`, `COMPLETED`, and `FAILED`.
- **FR-006**: The system MUST make a URL available upon successful completion that allows anyone to view the generated catalog website.
- **FR-007**: The system MUST preserve the same public catalog URL across subsequent update requests so existing links remain valid.
- **FR-008**: The system MUST reject requests from unauthenticated callers or merchants requesting catalogs for accounts they do not own.
- **FR-009**: The system MUST record a human-readable reason when a job transitions to `FAILED` state.
- **FR-010**: The system MUST prevent a merchant from running more than one catalog generation job at the same time; if a new request arrives while one is active, the system MUST either queue it or supersede the in-progress job and document the behavior.

### Key Entities

- **CatalogJob**: Represents a single catalog generation or update request. Attributes: job ID, owning merchant, status, submitted-at, completed-at, catalog URL (on success), failure reason (on failure).
- **ProductCatalog**: The generated website artifact. Attributes: merchant association, catalog URL, last-generated timestamp, list of branches and their products reflected.
- **MerchantBranch**: (existing entity) A physical or logical branch of a merchant, including its name, location, and associated products.
- **Product**: (existing entity) A sellable item belonging to a merchant branch, including name, description, price, and availability.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Merchants receive a job reference within 1 second of submitting a catalog generation or update request.
- **SC-002**: Catalog generation for a merchant with up to 50 products across up to 10 branches completes within 3 minutes under normal load.
- **SC-003**: The generated catalog website accurately reflects 100% of the merchant's current branch and product data at the time the job was started.
- **SC-004**: The catalog public URL remains stable across update operations — zero URL changes for existing catalogs.
- **SC-005**: The status endpoint returns current job state within 500 milliseconds for any active or historical job.
- **SC-006**: The system handles at least 20 concurrent catalog generation jobs without degraded response times on the submission or status endpoints.

## Assumptions

- Merchant authentication and identity are handled by the existing authentication mechanism; this feature does not introduce new login or session management.
- Merchant branch and product data are already stored in the system and accessible to the catalog generation pipeline.
- The generated catalog website is hosted by the platform; merchants do not need to manage their own hosting.
- The initial catalog website format is a readable, browser-accessible web page; advanced customization (themes, custom domains) is out of scope for v1.
- Products are organized under branches; a product belongs to at least one branch.
- Multi-language catalog support is out of scope for v1; the catalog is generated in a single language derived from the merchant's data.
- Job history is retained for at least 30 days to support status inquiries and audit purposes.
- The async nature of the API means callers are responsible for polling the status endpoint or receiving a webhook/notification; push notification support is a future enhancement and out of scope for v1.
