# Implementation Plan: Voice Query and Spoken Response

**Branch**: `010-voice-transcription-response` | **Date**: 2026-04-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/010-voice-transcription-response/spec.md`

## Summary

Introduce a new top-level `voice` Spring Modulith module that accepts mobile audio uploads, transcribes them through a provider-agnostic speech abstraction, generates a BlitzPay-scoped answer, synthesizes that answer back to audio, and returns a mobile-playable response in a single `/v1/voice/query` call. The design keeps raw audio ephemeral, uses explicit endpoint-level authentication, and adds a small public query surface in `payments.push.api` so voice responses can reference recent payment activity without violating module boundaries.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, Jackson Kotlin module, Bean Validation, Spring AI BOM-managed client libraries  
**Storage**: PostgreSQL 16 in schema `blitzpay` for payment-context metadata only; voice audio/transcripts are processed in-flight and not persisted  
**Testing**: JUnit 5, Mockito Kotlin, WebTestClient contract tests, Spring Modulith verification tests  
**Target Platform**: Linux server on JVM serving authenticated mobile clients  
**Project Type**: REST web-service with Spring Modulith business modules  
**Performance Goals**: End-to-end voice round trip under 10 seconds for recordings up to 30 seconds; graceful rejection for unsupported, oversized, or unusable audio  
**Constraints**: URL-path versioning under `/v1`; Liquibase owns schema changes; new HTTP behavior needs contract tests; no cross-module internal bean access; provider/model identifiers stay configuration-driven; raw audio is not stored durably  
**Scale/Scope**: Initial English-only, single-turn voice interactions, at least 50 concurrent submissions, initial contextual support focused on recent payment activity and supported BlitzPay topics

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin-only application code | ✅ | No Java source planned |
| Spring Modulith boundaries respected | ✅ | New `voice` module uses public `payments.push.api` contracts instead of internal repository access |
| API versioning and stable contracts | ✅ | New endpoint stays under `/v1/...`; contract tests and contract docs are part of the plan |
| Liquibase owns schema | ✅ | Only payment-context enrichment uses Liquibase; no schema is created for voice payload storage |
| Table prefix matches owning leaf module | ✅ | Any new payment-context columns stay on `push_*` tables owned by `payments.push` |
| Tests for every behavior change | ✅ | Unit, contract, and modulith verification updates are planned |
| Validate external input at boundaries | ✅ | Multipart size/type/duration validation and explicit auth guard live in the controller boundary |

## Project Structure

### Documentation (this feature)

```text
specs/010-voice-transcription-response/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── voice-api.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── voice/
│   ├── api/
│   ├── config/
│   ├── internal/
│   └── web/
├── payments/
│   └── push/
│       ├── api/
│       ├── internal/
│       └── persistence/
├── merchant/
│   └── api/
└── config/

src/main/resources/
└── db/changelog/

src/test/kotlin/com/elegant/software/blitzpay/
├── voice/
├── payments/
└── ModularityTest.kt

src/contractTest/kotlin/com/elegant/software/blitzpay/
├── voice/
└── payments/
```

**Structure Decision**: Use a new `voice` leaf module because voice orchestration is a distinct product capability with its own HTTP boundary, provider configuration, and error model. Reuse existing module APIs for domain context; where those APIs are insufficient, extend the owning module’s `api` package rather than coupling to internal repositories.

## Phase 0 Research Output

Phase 0 resolves the main design unknowns as follows:

1. Provider integration is isolated behind internal voice ports so the concrete provider can be swapped without hard-wiring the rest of the module to a single SDK.
2. Authentication is enforced at the endpoint because the repository has no shared security module yet.
3. Recent-payment contextual answers require a new public query contract in `payments.push.api` plus subject linkage on payment lifecycle data.
4. Mobile compatibility is optimized around multipart upload input and MP3 output.

## Phase 1 Design

### Module Design

- `voice.web.VoiceQueryController` handles `/v1/voice/query`, rejects missing or malformed auth/audio, and returns either `audio/mpeg` or `application/problem+json`.
- `voice.internal.VoiceQueryService` orchestrates `transcribe -> build context -> generate answer -> synthesize`.
- `voice.internal` defines provider-facing ports such as `SpeechTranscriptionClient`, `VoiceReasoningClient`, and `SpeechSynthesisClient`.
- `voice.config` binds `blitzpay.voice.*` properties and wires the provider adapters using Spring AI where practical and a thin fallback adapter where Spring AI coverage is incomplete.
- `payments.push.api` exposes a read model for recent user payment summaries consumed by the voice module.

### Data and Context Design

- Raw audio bytes, transcript text, and synthesized audio remain ephemeral and are not stored in the database.
- To support account-aware voice answers, payment lifecycle records are enriched with the authenticated caller subject at payment creation time and exposed through a `payments.push.api` gateway.
- Context assembly remains bounded: the voice module asks other modules for already-shaped summaries and never composes SQL or reaches into another module’s persistence package.

### Test Design

- Contract tests cover happy path, auth rejection, multipart validation, unsupported media types, unusable speech, and upstream AI failures.
- Unit tests cover controller validation, prompt/context assembly, provider error mapping, and synthesis response formatting.
- Modulith verification continues to guard against accidental cross-module bean coupling.

## Post-Design Constitution Check

| Rule | Status | Notes |
|------|--------|-------|
| Kotlin-only application code | ✅ | Design remains Kotlin-only |
| Spring Modulith boundaries respected | ✅ | Cross-module interaction uses `payments.push.api` read contracts |
| API versioning and stable contracts | ✅ | `POST /v1/voice/query` is the only new public endpoint in v1 |
| Liquibase owns schema | ✅ | Payment-context enrichment is delivered via Liquibase |
| Table prefix matches owning leaf module | ✅ | Any new/renamed payment columns remain on `push_*` tables |
| Tests for every behavior change | ✅ | Unit + contract + modulith verification retained |

## Implementation Phases

### Phase A - Public contracts and configuration

1. Add `voice` module package structure and module metadata.
2. Add `blitzpay.voice.*` configuration properties for provider selection, model IDs, upload limits, and prompt policy.
3. Add/update contract documentation for `/v1/voice/query`.

### Phase B - Payment context enablement

1. Extend payment request initiation to capture authenticated caller subject.
2. Add Liquibase changes to persist subject linkage on the `push` payment lifecycle data owned by `payments.push`.
3. Expose a `payments.push.api` gateway that returns recent payment summaries by subject.

### Phase C - Voice pipeline

1. Implement multipart controller validation and RFC 9457 error mapping.
2. Implement transcription, domain-context, answer-generation, and synthesis orchestration.
3. Wire the provider adapters behind internal ports.

### Phase D - Test and docs completion

1. Add `voice` unit tests and contract tests.
2. Update existing modulith verification tests if package exposure changes.
3. Update `README.md` or relevant docs if new env vars become required during implementation.

## Complexity Tracking

No constitution violations are currently required.
