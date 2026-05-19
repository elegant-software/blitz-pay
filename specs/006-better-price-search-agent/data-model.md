# Data Model: Real Market Search Price Savings Agent
# Data Model: Product Price Savings Agent

## ProductLookupRequest

Fields:
- `inputPrice: BigDecimal`
- `currency: String`
- `productTitle: String?`
- `brandName: String?`
- `modelName: String?`
- `sku: String?`
- `additionalAttributes: Map<String, String>`

Validation:
- `inputPrice` must be positive.
- At least one product identifier must be present.
- Empty strings should be normalized to null/blank-safe values before orchestration.

## KoogResearchSession

Purpose:
- Represents the single KOOG-run context created per incoming request.

Fields:
- `sessionId: String`
- `systemPromptVersion: String`
- `selectedProvider: String`
- `createdAt: Instant`
- `currentStage: MonitoringStage`
- `progress: Int`

Relationships:
- Created from one `ProductLookupRequest`
- Invokes tool calls that produce `MarketDiscoveryBatch` and `PriceComparisonResult`

Lifecycle:
- `created`
- `running`
- `completed`
- `failed`

## MarketDiscoveryBatch

Purpose:
- Normalized discovery result produced by any configured provider.

Fields:
- `provider: String`
- `queries: List<String>`
- `hits: List<MarketSearchHit>`
- `warnings: List<MonitoringWarning>`
- `bottleneck: MonitoringBottleneck?`

## MarketSearchHit

Fields:
- `url: String`
- `title: String?`
- `snippet: String?`
- `domain: String`
- `rank: Int`
- `sourceQuery: String`

Validation:
- `url` must be absolute.
- `domain` is derived from the normalized URL.

## MatchedProduct

Fields:
- `productTitle: String`
- `brandName: String?`
- `modelName: String?`
- `sku: String?`
- `confidence: BigDecimal?`

## ComparableOffer

Fields:
- `sellerName: String`
- `offerUrl: String`
- `price: BigDecimal`
- `currency: String`
- `availability: String?`
- `sourceProvider: String`
- `matchingSignals: List<String>`

Validation:
- `price` must be positive.
- `currency` must match the request currency to qualify for direct savings calculation.

## PriceSavingsResult

Fields:
- `status: PriceComparisonStatus`
- `inputPrice: BigDecimal`
- `currency: String`
- `matchedProduct: MatchedProduct?`
- `bestOffer: ComparableOffer?`
- `savingsAmount: BigDecimal`
- `savingsPercentage: BigDecimal?`
- `consideredOfferCount: Int`
- `excludedOfferCount: Int`
- `explanationCode: String`
- `monitoring: RequestMonitoringSnapshot`

States:
- `better_price_found`
- `no_better_price_found`
- `comparison_unavailable`

## RequestMonitoringSnapshot

Fields:
- `stage: String`
- `progress: Int`
- `warnings: List<MonitoringWarning>`
- `bottleneck: MonitoringBottleneck?`
- `failure: MonitoringFailure?`

Notes:
- This remains the outward-facing monitoring contract even when KOOG runs internally.
- KOOG lifecycle and tool-call progress are translated into these fields.

## MonitoringWarning

Fields:
- `code: String`
- `detail: String`

## MonitoringBottleneck

Fields:
- `code: String`
- `detail: String`
- `dependency: String?`

## MonitoringFailure

Fields:
- `code: String`
- `message: String`
- `retriable: Boolean`

## Relationships Summary

- One `ProductLookupRequest` creates one `KoogResearchSession`.
- One `KoogResearchSession` can issue multiple market-search tool calls.
- Market-search results feed deterministic price comparison.
- One `PriceSavingsResult` is returned per request and includes one `RequestMonitoringSnapshot`.
## Overview

The feature remains a stateless request/response flow. Live market discovery is provider-driven, but all providers feed the same normalized discovery and offer-extraction pipeline before savings logic runs.

## Entities

### Product Lookup Request

**Purpose**: Represents one incoming price-comparison request.

**Fields**:
- `inputPrice`
- `currency`
- `productTitle`
- `brandName`
- `modelName`
- `sku`
- `additionalAttributes`

**Validation Rules**:
- Input price must be positive
- Currency must be present
- At least one product identifier must be present

### Market Search Query

**Purpose**: Normalized query constructed from the incoming request for whichever market-search provider is active.

**Fields**:
- `queryText`
- `brandName`
- `modelName`
- `sku`
- `currency`
- `maxHits`

**Validation Rules**:
- Query text must be non-empty
- Search-result window must stay within configured bounds

### Market Search Provider Selection

**Purpose**: Represents configuration that selects the active market-search provider implementation for a request flow.

**Fields**:
- `providerName`: `fixture`, `brave`, or `deepsearch`
- `enabled`
- `requestTimeout`
- `maxHits`
- `maxPagesToFetch`

**Validation Rules**:
- Provider name must map to a supported implementation
- Runtime provider configuration must not change the external response contract

### Search Hit

**Purpose**: One candidate result returned by a discovery provider before retailer-page extraction.

**Fields**:
- `title`
- `url`
- `displayUrl`
- `snippet`
- `rank`
- `providerName`

**Validation Rules**:
- URL must be absolute
- Rank must be positive
- Provider name must be preserved for monitoring and traceability

### Extracted Market Offer

**Purpose**: A normalized offer extracted from a fetched retailer page.

**Fields**:
- `sellerName`
- `offerPrice`
- `currency`
- `availability`
- `productUrl`
- `normalizedTitle`
- `brandName`
- `modelName`
- `sku`
- `extractionEvidence`

**Validation Rules**:
- Price and currency must both be present for a comparable offer
- Product URL must reflect the fetched retailer page or canonical product page
- Extraction evidence must identify what structured fields were observed

### Extraction Evidence

**Purpose**: Explains how an extracted market offer was formed from live page data.

**Fields**:
- `sourceType`: `json_ld`, `microdata`, `meta_tags`, or `fallback_text`
- `observedFields`
- `pageTitle`
- `sellerHint`

**Validation Rules**:
- Source type must be explicit
- Observed fields must identify the structured values used

### Matched Product

**Purpose**: Represents the normalized product identity selected for comparison.

**Fields**:
- `normalizedTitle`
- `brandName`
- `modelName`
- `sku`
- `matchConfidence`
- `matchEvidence`

### Comparable Offer

**Purpose**: Represents one normalized market offer considered by the comparison engine.

**Fields**:
- `sellerName`
- `offerPrice`
- `currency`
- `productUrl`
- `availability`
- `qualificationStatus`
- `qualificationNotes`

### Request Monitoring Snapshot

**Purpose**: Represents the machine-readable operational view of the active request.

**Fields**:
- `stage`
- `progress`
- `warnings`
- `bottleneck`
- `failure`

**Lifecycle Stages**:
- `received`
- `validation`
- `search_discovery`
- `offer_extraction`
- `matching`
- `comparison`
- `completed`
- `failed`

### Price Comparison Result

**Purpose**: Represents the full structured output returned to callers.

**Fields**:
- `status`
- `inputPrice`
- `currency`
- `matchedProduct`
- `bestOffer`
- `savingsAmount`
- `savingsPercentage`
- `consideredOfferCount`
- `excludedOfferCount`
- `explanationCode`
- `monitoring`

## Relationships

- A **Product Lookup Request** produces one **Market Search Query**
- One **Market Search Provider Selection** determines which discovery adapter fulfills the **Market Search Query**
- One **Market Search Query** returns zero or more **Search Hit**
- One **Search Hit** may yield zero or one **Extracted Market Offer**
- A **Matched Product** is resolved from zero or more **Extracted Market Offer**
- A **Price Comparison Result** includes one **Request Monitoring Snapshot**

## State Considerations

### Live Search Lifecycle

1. Request accepted
2. Validation
3. Search query built
4. Active provider selected from configuration
5. Search discovery provider called
6. Candidate retailer pages fetched
7. Structured data extracted into market offers
8. Product matching and qualification run
9. Comparison result emitted

### Failure States

- Invalid request before search begins
- Configured provider is unsupported or unavailable
- Search provider failure or timeout
- Retailer pages fetched but no structured offer extracted
- Offers extracted but no reliable product match
- Offers matched but no cheaper qualifying offer exists
