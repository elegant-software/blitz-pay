# Implementation Plan: Product Price Savings Agent

**Branch**: `[006-product-price-savings-agent]` | **Date**: 2026-03-31 | **Spec**: `/Users/mehdi/MyProject/blitz-pay/specs/006-product-price-savings-agent/spec.md`
**Input**: Feature specification from `/Users/mehdi/MyProject/blitz-pay/specs/006-product-price-savings-agent/spec.md`

## Summary

Refactor the current price-comparison flow so each request is orchestrated by a Kotlin KOOG agent instead of a direct service path, while preserving the existing stateless market-search, price-comparison, monitoring, and Swagger semantics. Replace the handwritten A2A controller with KOOG's built-in A2A server modules so the product agent is exposed through a real protocol-compliant listener on the configured A2A port/path.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Jackson Kotlin module, springdoc OpenAPI, Jsoup, Browserbase-backed DeepSearch integration, KOOG agent runtime (`ai.koog:koog-agents`), KOOG A2A server modules (`ai.koog:a2a-server`, `ai.koog:agents-features-a2a-server`, `ai.koog:a2a-transport-server-jsonrpc-http`), Ktor Netty  
**Storage**: N/A; request handling remains stateless and in-memory only for the lifetime of a request  
**Testing**: JUnit 5, Spring Boot Test/WebFlux Test, Mockito Kotlin, Modulith test support, contract tests via `contractTest` source set  
**Target Platform**: JVM server application exposing HTTP and A2A endpoints  
**Project Type**: Single Spring Boot web service with Modulith modules  
**Performance Goals**: Return a structured response in the same synchronous request flow while keeping monitoring progress accurate for long-running provider calls  
**Constraints**: No persistence, keep the external API contract stable, preserve provider configurability, keep Swagger-visible monitoring fields, use KOOG as the real orchestrator rather than a passive wrapper  
**Scale/Scope**: Single-product comparison requests, one KOOG agent run per request, fixture/Brave/DeepSearch provider support

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Constitution file is still template-only and defines no enforceable gates.
- Repository guidance in `AGENTS.md` and current feature artifacts require Gradle, focused changes, fixture-backed tests where appropriate, and documentation updates for behavior/config changes.
- Planned design passes those practical gates: Gradle-only, no persistence added, tests/docs updated, and changes remain focused on the product price agent flow.

## Phase 0 Research

- Confirm official KOOG integration pattern for JVM/Spring applications.
- Decide whether to use raw `koog-agents` or Spring Boot starter for this repo.
- Decide how KOOG tools should wrap existing `marketsearch` and `pricecomparison` modules.
- Decide how to map KOOG runtime events into the existing monitoring response.
- Decide how to expose KOOG-native A2A behavior while keeping the direct HTTP result contract stable.

## Phase 1 Design Decisions

- Add KOOG runtime support to the Gradle build and configuration model.
- Keep `marketsearch` and `pricecomparison` as bounded modules; expose them to the KOOG agent through tool adapters rather than moving comparison logic into prompts.
- Introduce a dedicated KOOG orchestration layer under the current agent package and route controllers through it.
- Preserve stateless request handling; do not enable KOOG memory/persistence features.
- Preserve the structured direct HTTP response DTOs while moving A2A transport concerns to KOOG's native server implementation.

## Project Structure

### Documentation (this feature)

```text
specs/006-product-price-savings-agent/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── product-price-agent-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/elegant/software/blitzpay/
├── config/
├── invoiceagent/
│   ├── a2a/
│   ├── api/
│   ├── application/
│   ├── config/
│   ├── koog/
│   └── tool/
├── marketsearch/
│   ├── api/
│   ├── application/
│   ├── config/
│   ├── domain/
│   ├── parser/
│   └── provider/
└── pricecomparison/
    ├── api/
    ├── application/
    ├── domain/
    └── provider/

src/test/kotlin/com/elegant/software/blitzpay/
├── invoiceagent/
├── marketsearch/
└── pricecomparison/

src/contractTest/kotlin/com/elegant/software/blitzpay/invoiceagent/
└── PriceComparisonAgentContractTest.kt
```

**Structure Decision**: Keep the existing single-module Spring Boot repository and current modulith boundaries. Add KOOG-specific orchestration classes under `src/main/kotlin/com/elegant/software/blitzpay/betterprice/agent/koog/`, add a dedicated KOOG A2A server bootstrap/executor under `src/main/kotlin/com/elegant/software/blitzpay/betterprice/agent/a2a/`, and leave `betterprice/search` plus `pricecomparison` as reusable tool-backed domain modules.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Introduce KOOG runtime and A2A server dependencies | The feature now explicitly requires a Kotlin KOOG agent and real KOOG-native A2A protocol support | Keeping the handwritten controller would continue advertising protocol capabilities the runtime does not actually provide |
