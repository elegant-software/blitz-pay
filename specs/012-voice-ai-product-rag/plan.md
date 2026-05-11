# Implementation Plan: Voice AI Product Assistant

**Branch**: `012-voice-ai-product-rag` | **Date**: 2026-04-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/012-voice-ai-product-rag/spec.md`

## Summary

Extend the existing `/v1/voice/query` endpoint so that after Whisper transcription, the transcript is passed to a locally-running Ollama model. The model extracts product intent (name keywords, quantity) from the transcript, the merchant's product catalog is queried for matches, and the response is returned as a polymorphic `AssistantResponse` with a `type` discriminator (`TRANSCRIPT`, `PRODUCT_RESULT`, or `NO_MATCH`). No new database tables are required — the existing `merchant_products` table supplies all catalog data.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring AI (BOM already imported), Ollama via `spring-ai-starter-ollama` (new), `spring-ai-openai` (existing — for Whisper)
**Storage**: PostgreSQL 16 via JPA + Liquibase — no new tables for this feature
**Testing**: JUnit 5 + Mockito Kotlin (unit), WebTestClient under `contract-test` profile (contract)
**Target Platform**: JVM server (Spring Boot application)
**Project Type**: Web service (REST API)
**Performance Goals**: Endpoint must respond within 5 seconds P95 under normal load (includes Whisper + Ollama + DB query)
**Constraints**: Ollama runs locally; no data leaves the server. Response is synchronous — client blocks until result is ready.
**Scale/Scope**: Per-merchant-branch product catalog; typically O(100s) products per branch. No vector store required.

## Constitution Check

| Gate | Status | Notes |
|------|--------|-------|
| Kotlin only — no Java source | ✅ Pass | All new files in Kotlin |
| Spring Modulith boundaries — voice → merchant via `@NamedInterface` only | ✅ Pass | New `MerchantProductCatalogGateway` in `merchant.api` |
| API versioning — `/v1/...` URL path | ✅ Pass | Extends existing `/v1/voice/query` |
| Response shape change (breaking) | ⚠️ Noted | TRANSCRIPT responses keep `transcript`/`language` fields for compat; `type` field is additive. Mobile client will be updated in the same release cycle. |
| Contract test required for every new response shape | ✅ Required | `VoiceQueryControllerContractTest` must be extended |
| No Liquibase migration needed | ✅ Pass | Uses existing `merchant_products` table |
| No new secrets committed | ✅ Pass | Ollama is local; no API key needed |
| New dependency justified | ✅ Pass | `spring-ai-starter-ollama` — Ollama already planned as local model; uses existing Spring AI BOM |
| SLF4J `LoggerFactory` not `mu.KotlinLogging` | ✅ Required | All new classes must use `LoggerFactory.getLogger(...)` |

## Project Structure

### Documentation (this feature)

```text
specs/012-voice-ai-product-rag/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── contracts/           ← Phase 1 output
│   └── voice-query-api.md
└── tasks.md             ← Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── voice/
│   ├── api/
│   │   ├── VoiceGateway.kt               ← UPDATE return type → AssistantResponse
│   │   ├── AssistantResponseModels.kt    ← NEW sealed class + subtypes
│   │   └── package-info.kt               (existing)
│   ├── config/
│   │   ├── VoiceConfiguration.kt         ← UPDATE add OllamaChatClient bean
│   │   └── VoiceProperties.kt            ← UPDATE add Ollama nested config block
│   ├── internal/
│   │   ├── VoiceModels.kt                ← UPDATE add ProductIntent, VoiceQueryRequest
│   │   ├── VoiceQueryService.kt          ← UPDATE orchestrate transcript → intent → search → response
│   │   ├── ProductIntentExtractor.kt     ← NEW Ollama-based intent extraction
│   │   └── ProductCatalogSearch.kt       ← NEW matches intent against merchant catalog
│   ├── web/
│   │   └── VoiceQueryController.kt       ← UPDATE add merchantId/branchId params, return AssistantResponse
│   └── package-info.kt                   (existing)
│
└── merchant/
    ├── api/
    │   ├── MerchantProductCatalogGateway.kt  ← NEW @NamedInterface for catalog search
    │   ├── CatalogSearchModels.kt            ← NEW CatalogProduct data class
    │   └── package-info.kt                   (existing)
    └── application/
        └── MerchantProductCatalogService.kt  ← NEW implements MerchantProductCatalogGateway

src/main/resources/
└── application.yml      ← UPDATE add blitzpay.voice.ollama config block

build.gradle.kts         ← UPDATE add spring-ai-starter-ollama dependency

src/contractTest/kotlin/com/elegant/software/blitzpay/voice/
└── VoiceQueryControllerContractTest.kt   ← UPDATE stub new response types; add PRODUCT_RESULT and NO_MATCH cases

src/test/kotlin/com/elegant/software/blitzpay/voice/
├── ProductIntentExtractorTest.kt         ← NEW unit tests
└── VoiceQueryServiceTest.kt              ← NEW unit tests for orchestration
```

**Structure Decision**: Single project layout (existing pattern). The `voice` module is extended in-place. A new `@NamedInterface` on `merchant.api` exposes catalog search without leaking repository or domain types across module lines.

## Complexity Tracking

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|-----------|---------------------------------------|
| Response type change on `/v1/voice/query` | Spec requires polymorphic response with `type` discriminator | A separate endpoint would fragment the feature and split the voice UX flow |
| New `@NamedInterface` `MerchantProductCatalogGateway` | Voice module must query merchant catalog without crossing module boundaries | Direct `MerchantProductRepository` injection from `voice` violates Modulith rules |
