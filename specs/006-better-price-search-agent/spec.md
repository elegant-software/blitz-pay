# Feature Specification: Product Price Savings Agent

**Feature Branch**: `[006-product-price-savings-agent]`  
**Created**: 2026-03-30  
**Status**: Draft  
**Input**: User description: "Change the agent problem statement from invoice creation to product price comparison. The agent should look at a particular product using title, brand name, and similar identifiers, compare the provided input price against better available prices, report how much the customer can save, return structured data only for now, and not store results anywhere."

## Clarifications

### Session 2026-03-30

- Q: How should API-based monitoring for the price save agent work? → A: Expose per-request status, progress, bottleneck, and exception details through the API without persistence.
- Q: How should DeepSearch be added to live market search? → A: Add DeepSearch as an additional provider behind the existing market-search gateway while keeping Brave and fixture support.

### Session 2026-03-31

- Q: How should request orchestration work for the product price savings feature? → A: Each request must create and use a Kotlin KOOG agent to perform the product better-price research and return the structured result.
- Q: How should KOOG agent status be exposed via API? → A: Add a separate status endpoint for the active request only.
- Q: How should the API correlate a comparison request with the active KOOG agent status endpoint? → A: The comparison API returns a generated requestId, and the status endpoint uses that requestId.
- Q: Should the comparison API stay synchronous or become asynchronous when a separate status endpoint is added? → A: Make comparison submission asynchronous: return requestId immediately, then use separate status and result endpoints.

### Session 2026-04-01

- Q: How should A2A protocol support be implemented for the product price agent? → A: Replace the handwritten Spring A2A facade with KOOG's built-in A2A server modules so the configured A2A port/path hosts a real protocol-compliant server with KOOG task events and agent-card metadata.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find Better Price for a Product (Priority: P1)

A user provides a product description and a current price they have been offered or have found, and a KOOG-based agent returns whether a better currently available price exists and how much could be saved.

**Why this priority**: This is the core user value. If the agent cannot tell whether a customer can save money on a product, the feature does not solve the primary problem.

**Independent Test**: Can be fully tested by submitting a product title or brand-backed product description plus an input price, receiving a requestId immediately, and confirming the result endpoint later returns the matched product, the best lower price found, and the savings amount for that requestId.

**Acceptance Scenarios**:

1. **Given** a user provides a product title and input price, **When** the agent finds one or more matching offers below that price, **Then** the result endpoint returns the best lower price and the absolute savings amount for that requestId.
2. **Given** a user provides a product title, brand, and input price, **When** the agent finds multiple relevant offers, **Then** the result endpoint returns structured comparison data for the best match and clearly indicates the compared product identity.
3. **Given** a valid product comparison request, **When** the request is accepted, **Then** the system creates a Kotlin KOOG agent for that request and uses it to drive the research flow asynchronously.
4. **Given** a valid product comparison request, **When** the request is accepted, **Then** the submission endpoint returns a generated requestId that the caller can use to query the active-request status endpoint and the result endpoint for that same KOOG agent execution.

---

### User Story 2 - Confirm No Savings Available (Priority: P2)

A user provides a product description and price, and the agent confirms when no better currently available price can be found.

**Why this priority**: Users need a trustworthy answer even when the result is negative. "No better price found" is still useful and avoids false savings claims.

**Independent Test**: Can be fully tested by submitting a product and input price where no lower qualifying offer exists and confirming the result endpoint explicitly states that no savings are available for the returned requestId.

**Acceptance Scenarios**:

1. **Given** a user provides a product description and input price, **When** the agent finds matching offers but none lower than the input price, **Then** the result endpoint returns a structured response showing zero savings and no recommended cheaper offer.
2. **Given** a user provides a product description and input price, **When** the agent cannot find any qualifying market offer, **Then** the result endpoint returns a structured response indicating that no comparison result is available.

---

### User Story 3 - Receive Machine-Readable Comparison Output (Priority: P3)

A downstream consumer wants the agent result in a structured format that can later be reused by other features without storing it yet after retrieving it by requestId.

**Why this priority**: The user explicitly wants structured data now and persistence later, so the result format must be usable without adding storage scope to this feature.

**Independent Test**: Can be fully tested by submitting a valid request, waiting for completion, and confirming the result endpoint output for the requestId contains consistent structured fields for product identity, price comparison outcome, and savings.

**Acceptance Scenarios**:

1. **Given** a valid product lookup request, **When** the agent returns a comparison result for the requestId, **Then** the result response uses a consistent structured schema rather than free-form prose.
2. **Given** a valid product lookup request, **When** the agent completes the comparison, **Then** the result response contains enough fields for another system to display the matched product, compared offers, and estimated savings without additional parsing.

---

### User Story 4 - Monitor Active Request Progress (Priority: P2)

A caller wants to observe what the agent is doing during a request, including current stage, progress, and bottlenecks or failure details, through a separate active-request status endpoint documented in Swagger.

**Why this priority**: Operational visibility materially affects supportability and trust in the agent, especially when external pricing lookups are slow or fail.

**Independent Test**: Can be fully tested by submitting a valid request, calling the active-request status endpoint while that request is in flight, and confirming the status response includes machine-readable monitoring fields for current stage, progress, warnings, and any failure details generated during that request.

**Acceptance Scenarios**:

1. **Given** a valid product comparison request, **When** the agent is processing the request, **Then** the separate active-request status endpoint returns the current execution stage and progress indicators for that request.
2. **Given** a product comparison request encounters an exception or slowdown, **When** the caller queries the active-request status endpoint, **Then** the status response includes machine-readable bottleneck or failure details for that same request.

### Edge Cases

- What happens when the user provides only partial product information, such as a title without brand or a brand without a precise model identifier?
- How does the system handle multiple similar products where the agent cannot confidently determine a single best product match?
- What happens when the input price is missing, zero, negative, or not expressed as a valid amount?
- How does the system respond when market pricing data is temporarily unavailable or incomplete?
- What happens when the cheapest available offer has extra conditions that make the comparison unreliable, such as missing seller information or unclear product identity?
- How does the system report the current processing stage and bottleneck details when a request slows down or fails before a comparison result is completed?
- What happens when KOOG agent creation or execution fails before live market research completes?
- What does the result endpoint return when the caller queries a requestId that is still running, expired, or unknown?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept a product lookup request containing an input price and at least one product identifier, such as product title, brand name, model name, SKU, or similar descriptive attributes.
- **FR-002**: The system MUST evaluate the provided identifiers to determine the most relevant product match for comparison.
- **FR-003**: The system MUST create a Kotlin KOOG agent for each product comparison request and use that agent as the orchestration layer for live product-price research.
- **FR-004**: The system MUST search available price information for offers that correspond to the matched product.
- **FR-005**: The system MUST compare the user's input price against qualifying lower-priced offers for the matched product.
- **FR-006**: The system MUST calculate the customer's potential savings as the difference between the input price and the best lower qualifying price.
- **FR-007**: The system MUST return a structured response that includes the matched product identity, the input price, the comparison outcome, and the calculated savings amount when a better price is found.
- **FR-008**: The system MUST explicitly indicate when no better qualifying price is found.
- **FR-009**: The system MUST explicitly indicate when no reliable product match or no comparable price result can be determined.
- **FR-010**: The system MUST provide enough structured detail about the selected comparison result for another system to present the recommendation without parsing narrative text.
- **FR-011**: The system MUST validate that the input price is a valid positive monetary amount before performing a comparison.
- **FR-012**: The system MUST avoid persisting product lookup requests, comparison results, or savings results as part of this feature.
- **FR-013**: The system MUST accept a product comparison request asynchronously and return a generated requestId within the submission request flow rather than the final comparison result.
- **FR-014**: The system MUST expose machine-readable per-request monitoring data through a separate API status endpoint documented in Swagger for the active comparison request.
- **FR-015**: The status endpoint response MUST include the current processing stage and a progress indicator that reflects the active request lifecycle.
- **FR-016**: When a request experiences slowdown, degraded dependency behavior, or an exception, the system MUST include machine-readable bottleneck, warning, or failure details in the same request flow.
- **FR-017**: The system MUST not require persistence or later retrieval to access monitoring details for a request.
- **FR-024**: The system MUST return a generated requestId when a product comparison request is accepted.
- **FR-025**: The system MUST expose a separate result endpoint that accepts the generated requestId and returns the structured comparison result when the KOOG agent finishes.
- **FR-018**: The system MUST support multiple market-search providers behind a shared provider interface rather than coupling comparison logic directly to a single provider implementation.
- **FR-019**: The supported market-search providers MUST include the existing fixture provider, the existing Brave-based provider, and a DeepSearch provider.
- **FR-020**: The system MUST normalize discovery results from each market-search provider into the same internal comparison input model before offer selection and savings calculation.
- **FR-021**: The active market-search provider MUST be selectable through application configuration without changing the external comparison API contract.
- **FR-022**: The KOOG agent MUST invoke the existing market-search and price-comparison capabilities as tools or equivalent bounded actions rather than bypassing those modules with separate ad hoc logic.
- **FR-023**: If KOOG agent creation or execution fails, the system MUST return a structured failure result through the result endpoint and expose machine-readable monitoring details through the status endpoint for the same requestId.
- **FR-026**: The active-request status endpoint MUST accept the generated requestId so a caller can query the correct in-flight KOOG agent status without relying on persistence.
- **FR-027**: The system MUST expose the product price agent through KOOG's built-in A2A server implementation rather than a handwritten controller that manually emulates the protocol envelope.
- **FR-028**: The configured `market-search.a2a.port` and `market-search.a2a.path` MUST correspond to a real listener started by the application for A2A traffic.
- **FR-029**: The A2A agent card MUST be produced from the same KOOG-native A2A server metadata used by the runtime listener so the advertised URL, transport, and capabilities are accurate.
- **FR-030**: The KOOG A2A integration MUST emit protocol-native task lifecycle updates for submitted, working, completed, and failed execution states instead of only returning a single handwritten completed/failed envelope.

### Key Entities *(include if feature involves data)*

- **Product Lookup Request**: The user-supplied comparison input, including product identifiers and the input price to beat.
- **Active Request Identifier**: The generated requestId that correlates one accepted comparison request with its in-flight KOOG agent status response.
- **Comparison Submission Response**: The asynchronous acceptance payload that confirms the request was accepted and returns the generated requestId.
- **Matched Product**: The normalized product identity the system determines is the best comparison target from the provided product details.
- **Comparable Offer**: A market offer considered relevant to the matched product for price comparison.
- **Price Savings Result**: The structured output describing whether a cheaper offer exists, what the best qualifying price is, and how much the customer could save.
- **Request Monitoring Snapshot**: The machine-readable operational view of the active request, including current stage, progress, and any warning, bottleneck, or exception details available during that request.
- **KOOG Research Agent**: The per-request Kotlin KOOG agent instance that orchestrates product-price research by invoking market-search and comparison capabilities and then returning the structured outcome.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In representative test scenarios with identifiable products, 95% of valid requests return a structured comparison result without manual interpretation.
- **SC-002**: For scenarios where a lower qualifying price exists, the reported savings amount matches the arithmetic difference between the input price and the selected lower price in 100% of tested cases.
- **SC-003**: In representative test scenarios where no lower qualifying price exists, 100% of responses clearly report that no savings are available instead of implying a recommendation.
- **SC-004**: In representative test scenarios with incomplete or ambiguous product information, 100% of responses clearly distinguish between "no reliable match" and "no cheaper offer found."
- **SC-005**: A downstream consumer can render the result from the returned structured fields alone in 100% of acceptance-test scenarios.
- **SC-006**: In representative test scenarios, 100% of active-request status endpoint responses include machine-readable monitoring fields for the active request’s stage and progress.
- **SC-007**: In representative failure or slowdown scenarios, 100% of active-request status endpoint responses expose machine-readable bottleneck or exception details for that same request without requiring separate historical lookup.
- **SC-008**: In representative test scenarios, 100% of accepted comparison requests return a non-empty requestId that successfully correlates to the matching active-request status response while the request remains in flight.
- **SC-009**: In representative test scenarios, 95% of accepted comparison requests produce a result endpoint response retrievable by requestId without manual correlation or additional lookup keys.

## Assumptions

- The existing Kotlin-based agent platform on this branch remains the execution environment for the feature.
- Kotlin KOOG is the required orchestration framework for this feature rather than an optional wrapper around a direct service-only flow.
- Live market-search provider integration is extensible and initially includes fixture, Brave, and DeepSearch-backed options behind the same gateway.
- This feature covers one product comparison request at a time rather than batch lookups.
- The first version returns a requestId immediately and exposes structured comparison data only through a separate result endpoint without saving requests, results, or history.
- Monitoring visibility is limited to the currently active request and is exposed through a separate status endpoint rather than a persisted execution-history feature.
- Seller checkout, alerting, user accounts, and historical tracking are out of scope for this feature.
- The KOOG-native A2A server may run on its own configured listener port alongside the main Spring Boot HTTP port.
