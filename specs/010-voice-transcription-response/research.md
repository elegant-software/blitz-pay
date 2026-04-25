# Research: Voice Query and Spoken Response

## 1. Module placement

**Decision**: Create a new top-level `com.elegant.software.blitzpay.voice` module.

**Rationale**: Voice orchestration is a standalone business capability with its own HTTP contract, provider configuration, prompt policy, and error mapping. Putting it under `payments` would incorrectly imply ownership by the payment domain and would make later expansion harder.

**Alternatives considered**:
- Reuse `payments` as the host module: rejected because speech processing is not payment execution.
- Fold into `merchant`: rejected because merchant is unrelated to the voice transport and provider lifecycle.

## 2. Provider abstraction strategy

**Decision**: Isolate all provider calls behind internal voice ports: transcription, reasoning, and synthesis.

**Rationale**: The feature requirement explicitly asks for vendor agnosticism. A module-level port boundary is the most reliable way to preserve that even if provider-specific Spring AI support differs across modalities.

**Alternatives considered**:
- Bind the module directly to one provider SDK end-to-end: rejected because it would make later replacement invasive.
- Avoid Spring AI entirely: rejected because Spring AI still provides useful abstractions for chat/audio paths where supported.

## 3. Spring AI usage

**Decision**: Use Spring AI at the adapter layer, not as the public architecture boundary of the module.

**Rationale**: The repo already imports the Spring AI BOM and uses Spring AI for MCP server support. Reusing Spring AI where it fits keeps the integration aligned with the requested direction, while an internal port boundary prevents the module from depending on the exact maturity of one Spring AI adapter for every audio feature.

**Alternatives considered**:
- Make Spring AI interfaces the only application-facing abstraction: rejected because it leaks provider-adapter shape into core voice orchestration.
- Build all adapters directly on `WebClient`: rejected as unnecessary duplication where Spring AI already helps.

## 4. Authentication approach

**Decision**: Enforce authentication explicitly in `VoiceQueryController` by requiring a bearer token and extracting the caller subject from the JWT payload, following the repository’s existing lightweight pattern.

**Rationale**: There is no shared security module in the current codebase. Waiting for a full security refactor would block the feature, while endpoint-level subject extraction is already used in `merchant.web.ProximityController`.

**Alternatives considered**:
- Introduce full Spring Security for this feature alone: rejected as too broad for the current repo state.
- Accept anonymous voice traffic and rely on upstream gateway auth: rejected because it violates FR-007.

## 5. Contextual response source

**Decision**: Add a read-only `payments.push.api` gateway for recent payment summaries keyed by authenticated subject, and use that gateway to enrich prompts for payment-related questions.

**Rationale**: The spec requires account-aware answers. The voice module cannot read `payments.push.persistence` directly under the Modulith rules, so the owning module must expose a stable summary API. This keeps contextual answers factual without breaking module boundaries.

**Alternatives considered**:
- Use only a static system prompt: rejected because it cannot satisfy the “actual account context” acceptance scenario.
- Query payment repositories directly from `voice`: rejected by constitution and architecture rules.

## 6. Payment subject linkage

**Decision**: Extend payment initiation and status persistence so the authenticated caller subject is stored with the payment lifecycle records owned by `payments.push`.

**Rationale**: There is currently no reliable way to answer “my latest payment” for an authenticated caller because `push_payment_status` is keyed only by `payment_request_id`. Adding caller-subject linkage is the smallest backend change that unlocks factual recent-payment summaries.

**Alternatives considered**:
- Require the mobile client to send a payment request ID with each voice query: rejected because it breaks the natural voice UX and does not meet the general “recent payment activity” scenario.
- Persist a separate voice-owned projection of payment history: rejected because payment status ownership belongs to `payments.push`.

## 7. Audio transport choice

**Decision**: Accept `multipart/form-data` with a required `audio` part and return `audio/mpeg` on success.

**Rationale**: Multipart upload matches existing Spring WebFlux controller patterns for binary input and keeps the client request simple. MP3 is the safest single output format for built-in iOS and Android playback.

**Alternatives considered**:
- Base64 audio in JSON: rejected because it inflates payload size and complicates client handling.
- Return WAV: rejected because payload size is larger with no client benefit.

## 8. Validation and failure contract

**Decision**: Map all validation and upstream failures to RFC 9457 `ProblemDetail` responses with stable reason codes.

**Rationale**: This matches existing controller behavior and gives the mobile app actionable failure modes for short audio, unsupported types, missing auth, and transient upstream provider failures.

**Alternatives considered**:
- Return provider-native error payloads: rejected because it leaks implementation detail to clients.
- Collapse all failures into HTTP 500: rejected because the mobile app needs actionable retry behavior.

## 9. Persistence scope

**Decision**: Do not persist raw audio, transcripts, or generated answers in the database for v1.

**Rationale**: The feature spec explicitly states recordings are processed in-flight and discarded. Persisting voice content would add schema, privacy, and retention obligations without helping the core flow.

**Alternatives considered**:
- Store transcripts for analytics: rejected as out of scope for v1.
- Store binary audio blobs: rejected by the feature assumptions and unnecessary for synchronous response generation.
