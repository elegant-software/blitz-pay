# Research: Voice AI Product Assistant

**Feature**: 012-voice-ai-product-rag
**Date**: 2026-04-28

---

## 1. Ollama Integration via Spring AI

**Decision**: Use `spring-ai-starter-ollama` (already covered by the existing `spring-ai-bom` import in `build.gradle.kts`). Configure via `spring.ai.ollama.*` properties pointing to the local Ollama server.

**Rationale**: Spring AI's Ollama starter provides `OllamaChatClient`, which takes a `Prompt` and returns a `ChatResponse`. It integrates with the existing Spring AI ecosystem already in use (`spring-ai-starter-mcp-server`, `spring-ai-openai`). No additional HTTP client is needed.

**Alternatives considered**:
- Raw Ollama REST calls via `WebClient` — more control, but re-implements retry/timeout logic already in Spring AI.
- LangChain4j — adds a second AI library dependency; rejected per CONSTITUTION "avoid libraries that duplicate functionality".

**Config block to add** (`application.yml`):
```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        model: ${OLLAMA_MODEL:llama3.2}
        options:
          temperature: 0.1
          num-predict: 256
```

**New env vars**:
- `OLLAMA_BASE_URL` — Ollama server URL (default: `http://localhost:11434`)
- `OLLAMA_MODEL` — model name to use (default: `llama3.2`)

---

## 2. Product Matching Strategy (RAG Without Vector Store)

**Decision**: Load all active products for the target branch from PostgreSQL, embed the full product list as context in the Ollama prompt, and ask Ollama to identify which product(s) match the transcript. Apply no pgvector/embedding layer.

**Rationale**: Merchant product catalogs are small (typically 10–200 items). Sending the entire catalog as a text-formatted JSON list in the Ollama prompt is fast, requires no schema changes, and avoids pgvector complexity. The LLM can perform fuzzy matching and multi-language normalization within the prompt.

**Prompt structure** (in `ProductIntentExtractor`):
```
You are a point-of-sale assistant. The customer said: "{transcript}"

Available products (JSON array):
{productListJson}

Instructions:
- If the customer is asking for a product, return a JSON object:
  {"matches": [{"productId": "uuid", "confidence": "high|medium|low"}], "quantity": 1}
  Rank by confidence. Return up to 5 matches. Set quantity from the customer's statement (default 1).
- If the request is not about any product in the list, return:
  {"matches": [], "quantity": null}
- Return only valid JSON. No explanation.
```

**Confidence mapping**:
- `high` → appears alone in result → PRODUCT_RESULT (single card)
- `medium`/`low` OR multiple → PRODUCT_RESULT (ranked shortlist, up to 5)
- empty `matches` → NO_MATCH

**Alternatives considered**:
- pgvector + embedding model — overkill for catalogs of this size; adds migration, new table, and a second model.
- PostgreSQL full-text search (tsvector) — good for large catalogs but requires a Liquibase migration to add a tsvector column and loses the LLM's fuzzy/cross-language capability.

---

## 3. Merchant/Branch Context in the Request

**Decision**: Add optional `merchantId` (UUID) and `branchId` (UUID) multipart form fields to the existing `POST /v1/voice/query` request. When both are present, the product reasoning pipeline is triggered. When absent, the endpoint falls back to transcript-only behavior (existing behavior, returns `TRANSCRIPT` type).

**Rationale**: The current `MerchantTenantFilter` extracts merchant context from URL path segments (`/merchants/{id}/...`). The voice endpoint at `/v1/voice/query` has no such segment. Adding form fields avoids a URL restructuring.

**Alternatives considered**:
- Move endpoint to `/v1/merchants/{merchantId}/branches/{branchId}/voice/query` — cleaner REST design but a breaking URL change that fragments the voice API surface.
- JWT-embedded merchant claim — would require changes to the auth token issuance flow outside the scope of this feature.

---

## 4. Polymorphic JSON Response

**Decision**: Use a Kotlin sealed class `AssistantResponse` with Jackson `@JsonTypeInfo(use = Id.NAME, property = "type")` and `@JsonSubTypes`. Place `AssistantResponse` (and its subtypes) in the `voice.api` package as part of the module's public contract surface.

**Sealed class hierarchy**:
```
AssistantResponse (abstract, type discriminator = "type")
├── Transcript(transcript, language)          → type = "TRANSCRIPT"
├── ProductResult(products, requestedQuantity) → type = "PRODUCT_RESULT"
└── NoMatch(message)                          → type = "NO_MATCH"
```

**Backward compatibility**: The `Transcript` subtype retains the `transcript` and `language` fields of the current `VoiceTranscriptionResponse`. Clients that read only `$.transcript` will continue to work for transcript-mode responses; they will receive unexpected types for product queries but that is expected since this is a new capability requiring client-side updates.

**Alternatives considered**:
- Generic `Map<String, Any>` response — no type safety; rejected.
- Separate endpoints per response type — splits the voice UX flow; rejected.

---

## 5. Cross-Module Access Pattern

**Decision**: Add `MerchantProductCatalogGateway` to `merchant.api` annotated with `@NamedInterface("MerchantProductCatalog")`. The voice module injects this interface. The implementation (`MerchantProductCatalogService`) in `merchant.application` calls the existing `MerchantProductRepository`.

**Rationale**: Spring Modulith requires cross-module references to go through `@NamedInterface`-exposed types. The `MerchantProductRepository` lives in `merchant.repository` (internal) and cannot be referenced by the `voice` module directly.

**Interface surface**:
```kotlin
fun findActiveProducts(merchantId: UUID, branchId: UUID): List<CatalogProduct>
```

Returns `List<CatalogProduct>` — a lightweight data class in `merchant.api` containing only the fields the voice module needs: `productId`, `branchId`, `name`, `description`, `unitPrice`, `imageUrl`.

---

## 6. Timeout and Fallback

**Decision**: The Ollama call is made with a configurable timeout (default: 4 000 ms, leaving 1 000 ms margin from the 5-second SLA). On timeout or Ollama unavailability, throw `UpstreamAiException` (existing class), which the controller maps to HTTP 502. Callers should retry or fall back to manual product browsing.

**Rationale**: The existing `UpstreamAiException` already models this scenario and the controller already maps it to a ProblemDetail 502 response. No new exception type needed.
