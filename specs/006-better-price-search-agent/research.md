# Research: Real Market Search with DeepSearch Provider
# Research: Product Price Savings Agent

## Decision: Use raw KOOG agents in the existing Spring Boot app

Rationale:
- Official KOOG docs show `ai.koog:koog-agents` as the core dependency for building `AIAgent` workflows.
- The current repo already has its own Spring Boot/WebFlux wiring, so a direct KOOG agent integration is enough for this feature.
- This keeps the LLM/agent lifecycle under explicit application control and avoids a larger starter-driven refactor.

Alternatives considered:
- `koog-spring-boot-starter`: viable, but broader than needed for the current focused refactor.
- Keep the current custom `InvoiceKoogAgentFactory` wrapper only: rejected because the spec now requires KOOG to be the actual request orchestrator.

## Decision: Use KOOG's built-in A2A server modules instead of a handwritten Spring controller

Rationale:
- Official KOOG A2A docs provide a dedicated `A2AServer`, transport layer, and `A2AAgentServer` feature for exposing Koog agents with protocol-compliant task lifecycle handling.
- The current custom `/a2a/market-search` controller only mimics one request/response envelope and does not start a real listener on the configured A2A port.
- Adopting the KOOG server removes misleading boilerplate and makes the configured agent-card metadata, listener port, and task events truthful.

Alternatives considered:
- Keep the current Spring A2A controller: rejected because it advertises A2A support without actually using KOOG's A2A server/runtime features.
- Use only the Spring Boot starter: rejected because the KOOG docs describe A2A support through dedicated A2A modules and transport setup, not through starter auto-configuration alone.

## Decision: Model the product research flow as a KOOG agent with bounded tools

Rationale:
- Official KOOG guidance centers around `AIAgent` plus `ToolRegistry` for external actions.
- The repo already has stable `marketsearch` and `pricecomparison` modules. Tool wrappers can call those modules without duplicating domain logic in prompts.
- This preserves testability and avoids turning the LLM prompt into the business rules implementation.

Alternatives considered:
- Let the KOOG agent directly own comparison logic inside the prompt: rejected because the price-comparison module already contains deterministic rules that should remain authoritative.
- Replace existing modules with agent-only logic: rejected because it would increase risk and reduce reuse.

## Decision: Preserve the synchronous HTTP/A2A contract and move KOOG inside the orchestration path

Rationale:
- The current API already returns structured result data and monitoring in the request/response flow.
- The spec does not ask for async job polling or persistence.
- A synchronous KOOG run per request fits the current caller contract while satisfying the new orchestration requirement.

Alternatives considered:
- Introduce async agent jobs with separate status lookup endpoints: rejected because it would violate the no-persistence/current-flow requirement.

## Decision: Map KOOG runtime events into the existing monitoring model

Rationale:
- KOOG supports runtime events/tracing concepts, which align with the existing monitoring requirement.
- The API already exposes `stage`, `progress`, warnings, bottleneck, and failure details.
- Keeping the current monitoring DTO avoids external contract churn while still making KOOG execution observable.

Alternatives considered:
- Expose raw KOOG event streams directly: rejected because it would change the public contract and leak framework details to API consumers.

## Decision: Keep Browserbase-backed DeepSearch, Brave, and fixture providers behind the existing `marketsearch` gateway

Rationale:
- The provider abstraction is already in place and recently debugged.
- KOOG should orchestrate research, not replace the provider integration boundary.
- This maintains configuration-driven provider selection and keeps market-search concerns separated from agent execution concerns.

Alternatives considered:
- Move provider-specific HTTP calls into KOOG tools directly: rejected because it would bypass the `marketsearch` module and violate the bounded-tool requirement.

## Decision: Keep KOOG stateless for this feature

Rationale:
- The spec explicitly rules out persistence.
- KOOG memory features are optional and unnecessary for single-request product lookup.
- A stateless per-request run is simpler and aligns with the existing response model.

Alternatives considered:
- Enable chat memory or other persistent Koog features: rejected because they add storage/lifecycle scope outside the feature.

## Sources

- KOOG overview: https://docs.koog.ai/
- KOOG basic agents: https://docs.koog.ai/agents/basic-agents/
- KOOG tools overview: https://docs.koog.ai/tools-overview/
- KOOG quickstart: https://docs.koog.ai/quickstart/
- KOOG A2A overview: https://docs.koog.ai/a2a-protocol-overview/
- KOOG A2A server: https://docs.koog.ai/a2a-server/
- KOOG A2A and Koog integration: https://docs.koog.ai/a2a-koog-integration/
## Decision 1: Keep `marketsearch` as the single integration boundary

**Decision**: Keep all live search discovery integrations inside the existing `marketsearch` Spring Modulith module and expose them through the existing published gateway consumed by `pricecomparison`.

**Rationale**: The repository already separates external search and extraction concerns from comparison logic. Adding DeepSearch as another provider inside `marketsearch` preserves those boundaries and stays consistent with the architecture guideline that cross-module access must go through published APIs only.

**Alternatives considered**:

- Add DeepSearch directly inside `pricecomparison`: rejected because it couples business comparison logic to provider-specific HTTP integration.
- Create a second parallel research/search module just for DeepSearch: rejected because it would duplicate discovery responsibilities and violate the focused-change rule.

## Decision 2: Model DeepSearch as a provider adapter, not a new comparison flow

**Decision**: Treat DeepSearch as another discovery provider behind the same provider interface used by fixture and Brave.

**Rationale**: The user explicitly asked to keep existing providers and add DeepSearch. A provider-adapter approach preserves the current API contract, keeps provider selection configuration-driven, and avoids branching the orchestration path based on provider-specific response types.

**Alternatives considered**:

- Replace Brave with DeepSearch: rejected because the spec now requires both providers to remain supported.
- Make DeepSearch a separate higher-level orchestration mode: rejected because it would create divergent monitoring and contract behavior for essentially the same user-facing feature.

## Decision 3: Normalize provider discovery output into the existing internal search-hit model

**Decision**: Map DeepSearch discovery results into the same internal `SearchHit` representation already used by live search processing.

**Rationale**: `pricecomparison` should remain unaware of whether candidate retailer URLs came from fixture data, Brave, or DeepSearch. A normalized search-hit model keeps extraction, matching, qualification, and monitoring semantics stable across providers.

**Alternatives considered**:

- Pass raw DeepSearch payloads downstream: rejected because it leaks provider-specific response contracts across module boundaries.
- Maintain separate extraction pipelines for each provider: rejected because retailer-page extraction is the common responsibility, not provider-specific discovery.

## Decision 4: Keep retailer-page extraction as the price authority

**Decision**: Continue using provider results for discovery only. Final comparable offers must still come from fetched retailer pages and structured data extraction rather than provider snippet text.

**Rationale**: Search providers help find candidate pages, but price comparison needs more defensible evidence than snippets. The existing design already uses structured product and offer data from retailer pages, which keeps Brave and DeepSearch aligned under one trustworthy extraction model.

**Alternatives considered**:

- Trust DeepSearch answer text or snippets as final prices: rejected because provider-generated summaries are not a stable pricing contract.
- Skip extraction for one provider if it returns richer summaries: rejected because it would make comparison accuracy and explainability provider-dependent.

## Decision 5: Preserve synchronous, bounded execution with provider-neutral monitoring

**Decision**: Keep the current synchronous request model and extend monitoring so provider-specific failures or bottlenecks still surface through the same request-scoped monitoring object.

**Rationale**: The spec still forbids persistence and later retrieval. A bounded synchronous flow with provider-neutral monitoring stages such as `search_discovery` and `offer_extraction` lets the caller see where Brave or DeepSearch is slow or failing without changing the external API shape.

**Alternatives considered**:

- Introduce async jobs for DeepSearch only: rejected because it breaks the stateless same-request monitoring rule.
- Add provider-specific monitoring schemas: rejected because Swagger and A2A consumers should not have to branch on provider to parse status information.

## Decision 6: Verify live-provider behavior with fixtures first and configuration second

**Decision**: Continue using fixture-backed canonical discovery and retailer-page payloads for deterministic verification, while treating Brave and DeepSearch as runtime-configured live providers.

**Rationale**: The constitution requires fixture-first reuse and discourages duplicated business data in tests. Provider-specific fixtures let the code prove normalization, monitoring, and failure handling without depending on outbound HTTP in CI.

**Alternatives considered**:

- Exercise live provider calls in standard tests: rejected because network variability would weaken repeatability.
- Hard-code small provider payloads inline in tests: rejected because reusable market-search scenarios belong in shared fixtures under `src/test/resources/testdata/`.
