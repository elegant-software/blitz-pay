# Contract: Product Price Agent with Real Market Search
# Contract: Product Price Savings Agent

## HTTP Endpoint

- Method: `POST`
- Path: `/v1/invoice-agent/chat`
- Content-Type: `application/json`

Note:
- The current external path remains stable for compatibility even though the active behavior is product-price research, not invoice creation.

## Request Body

```json
{
  "inputPrice": 38.00,
  "currency": "EUR",
  "productTitle": "Frontline Spot On Dog S Solution",
  "brandName": "FRONTLINE",
  "modelName": "",
  "sku": "",
  "additionalAttributes": {
    "locale": "de-DE"
  }
}
```

Rules:
- `inputPrice` is required and must be positive.
- `currency` is required.
- At least one of `productTitle`, `brandName`, `modelName`, `sku`, or a non-empty attribute must identify the product.

## Successful Response

```json
{
  "status": "better_price_found",
  "inputPrice": 38.00,
  "currency": "EUR",
  "matchedProduct": {
    "productTitle": "Frontline Spot On Dog S Solution",
    "brandName": "FRONTLINE",
    "modelName": null,
    "sku": null
  },
  "bestOffer": {
    "sellerName": "Example Retailer",
    "offerUrl": "https://example.com/frontline-product",
    "price": 31.99,
    "currency": "EUR",
    "availability": "in_stock",
    "sourceProvider": "deepsearch"
  },
  "savingsAmount": 6.01,
  "savingsPercentage": 15.82,
  "consideredOfferCount": 2,
  "excludedOfferCount": 1,
  "explanationCode": "better_price_found",
  "monitoring": {
    "stage": "completed",
    "progress": 100,
    "warnings": [],
    "bottleneck": null,
    "failure": null
  }
}
```

## Negative Outcomes

### No lower price found

```json
{
  "status": "no_better_price_found",
  "bestOffer": null,
  "savingsAmount": 0,
  "explanationCode": "no_better_price_found"
}
```

### Comparison unavailable

```json
{
  "status": "comparison_unavailable",
  "bestOffer": null,
  "savingsAmount": 0,
  "explanationCode": "no_comparable_offers",
  "monitoring": {
    "stage": "completed",
    "progress": 100,
    "warnings": [
      {
        "code": "partial_results",
        "detail": "deepsearch search hit did not expose extractable structured offer data"
      }
    ],
    "bottleneck": null,
    "failure": null
  }
}
```

### KOOG orchestration failure

```json
{
  "status": "comparison_unavailable",
  "bestOffer": null,
  "savingsAmount": 0,
  "explanationCode": "agent_execution_failed",
  "monitoring": {
    "stage": "failed",
    "progress": 20,
    "warnings": [],
    "bottleneck": null,
    "failure": {
      "code": "agent_execution_failed",
      "message": "KOOG agent execution failed before price comparison completed",
      "retriable": true
    }
  }
}
```

## KOOG Internal Contract

Expected orchestration:
- One KOOG `AIAgent` instance is created per request.
- The agent uses a bounded `ToolRegistry`.
- Tools must wrap existing module capabilities rather than duplicate them.

Required tool intents:
- `market_search`
  - Input: normalized request identifiers and currency hints
  - Output: normalized discovery hits or extracted offers
- `price_compare`
  - Input: normalized request plus candidate offers
  - Output: deterministic `PriceSavingsResult`

Monitoring expectations:
- Agent startup, tool invocation, provider lookup, comparison completion, and failures must be translated into the public `monitoring` object.

## A2A Compatibility

- A2A semantics remain aligned with the HTTP contract.
- Structured result fields must stay equivalent.
- Serialized A2A text payloads may carry JSON text, but the underlying result semantics must match the HTTP response.
## Purpose

Define the external contract for live product discovery, provider-neutral offer extraction, savings reporting, and active-request monitoring.

## Contract Scope

This contract applies to the direct HTTP endpoint and the A2A-compatible agent flow. It does not add persistence or historical monitoring retrieval. Provider selection is an internal configuration concern and must not change the external request or response shape.

## Direct HTTP Contract

### Endpoint

- `POST /v1/invoice-agent/chat`

### Request Shape

The request body must support:

- `inputPrice`
- `currency`
- at least one product identifier:
  - `productTitle`
  - `brandName`
  - `modelName`
  - `sku`
  - `additionalAttributes`

### Response Shape

The response must be JSON and must include:

- `status`
- `matchedProduct`
- `inputPrice`
- `currency`
- `bestOffer` when available
- `savingsAmount`
- `savingsPercentage` when available
- `consideredOfferCount`
- `excludedOfferCount`
- `explanationCode`
- `monitoring`

### Best Offer Shape

When `bestOffer` is present it must include:

- `sellerName`
- `offerPrice`
- `currency`
- `productUrl`
- `availability`
- `qualificationStatus`
- `qualificationNotes`

### Monitoring Shape

The `monitoring` object must include:

- `stage`
- `progress`
- `warnings` when relevant
- `bottleneck` when relevant
- `failure` when relevant

Monitoring details may include provider context, but the object shape must stay stable across fixture, Brave, and DeepSearch-backed executions.

### Monitoring Stage Contract

The implementation must use request stages that can represent live market search, including:

- `validation`
- `search_discovery`
- `offer_extraction`
- `matching`
- `comparison`
- `completed`
- `failed`

### Outcome Contract

- `better_price_found`: at least one qualifying lower offer was extracted from live market search results
- `no_better_price_found`: qualifying live offers were found, but none beat the input price
- `comparison_unavailable`: no reliable live match or no comparable extracted offer set was available

## Search Integration Contract

- Search-provider discovery output must be normalized into the same internal search-hit model before retailer-page extraction
- Search-result snippets alone are not sufficient as the final price-comparison evidence
- Comparable offers must be built from fetched retailer-page data
- The implementation may exclude search hits that do not yield extractable structured offer data
- The implementation must support `fixture`, `brave`, and `deepsearch` providers behind the same gateway

## Swagger Contract

- The direct HTTP schema must document monitoring fields in the same response model as the comparison result
- Swagger/OpenAPI must describe the request as a live product price-comparison operation rather than invoice generation
- Swagger/OpenAPI must not require provider-specific request bodies or alternate response schemas

## A2A Contract

### Message Flow

- `{market-search.a2a.port}` and `{market-search.a2a.path}` must identify a real KOOG A2A listener
- The A2A transport must be provided by KOOG's server/JSON-RPC transport modules rather than a handwritten Spring controller
- The A2A agent card metadata must advertise the actual A2A URL and supported protocol version
- Structured product-price result payloads must remain available to A2A clients, even when the protocol uses task events and server-managed task state

### Result Contract

- A2A `completed` means a structured response was returned, including no-savings and comparison-unavailable cases
- A2A `failed` means the request could not be processed at all

## Non-Persistence Contract

- Search requests, extracted offers, comparison results, and monitoring details are scoped to the active request only
- No stored execution history or later retrieval endpoint is introduced by this feature
