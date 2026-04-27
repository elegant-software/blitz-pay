# Data Model: Voice AI Product Assistant

**Feature**: 012-voice-ai-product-rag
**Date**: 2026-04-28

---

## No New Database Tables

All product data is sourced from the existing `blitzpay.merchant_products` table (owned by the `merchant` module). No Liquibase migrations are required for this feature.

---

## New Kotlin Types

### `AssistantResponse` (sealed class — `voice.api`)

The polymorphic response returned by `VoiceGateway.process()` and serialized to JSON by the controller. The `type` field acts as the discriminator.

```
AssistantResponse
│
├── Transcript
│   ├── type: String = "TRANSCRIPT"
│   ├── transcript: String          — the raw transcription text
│   └── language: String?           — BCP-47 language code, if detected
│
├── ProductResult
│   ├── type: String = "PRODUCT_RESULT"
│   ├── products: List<ProductMatch> — ranked list, 1–5 items
│   └── requestedQuantity: Int?     — quantity extracted from transcript (null = not stated)
│
└── NoMatch
    ├── type: String = "NO_MATCH"
    └── message: String             — friendly explanation, e.g. "I didn't understand that request..."
```

### `ProductMatch` (data class — `voice.api`)

Represents a single matched product, embedded in `ProductResult.products`. Contains exactly the fields needed for the client to render a product card and invoke basket add.

```
ProductMatch
├── productId: UUID
├── branchId: UUID
├── name: String
├── description: String?
├── unitPrice: BigDecimal
└── imageUrl: String?         — pre-signed download URL (may be null if no image)
```

### `ProductIntent` (data class — `voice.internal`)

Internal model produced by `ProductIntentExtractor`. Never leaves the voice module.

```
ProductIntent
├── matchedProductIds: List<UUID>   — ordered by Ollama confidence (best first)
└── requestedQuantity: Int?         — quantity from transcript; null if not stated
```

### `VoiceQueryRequest` (update to `VoiceAudioSubmission` — `voice.internal`)

The existing `VoiceAudioSubmission` is extended with two optional merchant context fields:

```
VoiceAudioSubmission (existing + additions)
├── bytes: ByteArray
├── contentType: String
├── filename: String?
├── sizeBytes: Long
├── callerSubject: String
├── merchantId: UUID?    ← NEW (null → transcript-only mode)
└── branchId: UUID?      ← NEW (null → transcript-only mode)
```

### `CatalogProduct` (data class — `merchant.api`)

Lightweight product projection exposed by `MerchantProductCatalogGateway` to the voice module. Does not expose JPA entity or internal repository types.

```
CatalogProduct
├── productId: UUID
├── branchId: UUID
├── name: String
├── description: String?
├── unitPrice: BigDecimal
└── imageUrl: String?
```

### `MerchantProductCatalogGateway` (interface — `merchant.api`, `@NamedInterface`)

```
MerchantProductCatalogGateway
└── findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct>
```

---

## Request Shape Change

`POST /v1/voice/query` gains two optional multipart fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `audio` | file | Yes | Audio recording (existing) |
| `merchantId` | string (UUID) | No | Merchant scope for product search |
| `branchId` | string (UUID) | No | Branch scope for product search |

When `merchantId` and `branchId` are both present → product reasoning pipeline runs.
When either is absent → existing transcript-only pipeline runs (returns `TRANSCRIPT` type).

---

## Response Shape Change

`POST /v1/voice/query` response (polymorphic, always `Content-Type: application/json`):

**TRANSCRIPT** (existing behavior, now with `type` field added):
```json
{
  "type": "TRANSCRIPT",
  "transcript": "What is my latest payment?",
  "language": "en"
}
```

**PRODUCT_RESULT** (new):
```json
{
  "type": "PRODUCT_RESULT",
  "requestedQuantity": 1,
  "products": [
    {
      "productId": "e3b0c442-...",
      "branchId": "a1b2c3d4-...",
      "name": "Erdbeer Becher",
      "description": "Fresh strawberry cup",
      "unitPrice": 3.50,
      "imageUrl": "https://..."
    }
  ]
}
```

**NO_MATCH** (new):
```json
{
  "type": "NO_MATCH",
  "message": "I didn't understand that request — try browsing the product screen."
}
```
