# Feature Specification: Voice AI Product Assistant

**Feature Branch**: `012-voice-ai-product-rag`
**Created**: 2026-04-27
**Status**: Draft
**Input**: User description: "When you get voice query response from Whisper making use of spring AI and Ollama model in local do reasoning, make use of RAG to call particular services like product info, for example if user asks 'I would like one Erdbeer Becher, please.' return product info selectable in the basket, similar to product screen but will be visible in AI assistant"

## Clarifications

### Session 2026-04-27

- Q: Should the product info be returned as a structured API response (data only, client renders UI) or as a server-driven UI action? → A: Structured API response — the backend returns product data; the client is responsible for rendering the product card and basket action.
- Q: Is the response delivery model synchronous or async? → A: Synchronous — the client sends the transcript, the server blocks and returns the product result in a single response within the 5-second SLA.
- Q: Should the product result come from the existing /v1/voice/query endpoint or a new dedicated endpoint? → A: Extend the existing endpoint — the response is polymorphic: it can be a transcript, a product data payload, or a mobile UI action directive depending on what the AI reasoning concludes.
- Q: Should unrecognized voice commands navigate the user to a screen, or stay within the AI assistant chat? → A: Stay within the chat — product results are shown as selectable cards inline in the AI assistant. Navigation actions are out of scope for v1. When the user's request cannot be understood as a valid command, the assistant replies with a friendly "I didn't understand that request — try browsing the product screen" style message; no product card is shown.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Voice-Driven Product Selection (Priority: P1)

A customer speaks a natural language request naming a product — for example "I would like one Erdbeer Becher, please." The AI assistant understands the intent, finds the matching product in the merchant's catalog, and presents it as a ready-to-add card identical in format to the product screen. The customer confirms with a single tap to add it to the basket.

**Why this priority**: This is the core end-to-end value of the feature. Everything else depends on this flow working first. It directly reduces friction for customers who prefer speaking over browsing.

**Independent Test**: Can be tested by submitting a voice query that names a specific product by its exact name, verifying the correct product card is returned with price, description, and an add-to-basket action — and that tapping the action adds it to the basket.

**Acceptance Scenarios**:

1. **Given** a customer is on the AI assistant screen, **When** they submit a voice query naming a product that exists in the merchant's catalog, **Then** the assistant returns a product card matching that item within 5 seconds, showing name, price, and an add-to-basket button.
2. **Given** a product card has been returned by the assistant, **When** the customer taps "Add to basket", **Then** the product is added to their basket exactly as if selected from the product screen.
3. **Given** a customer names a product with a quantity ("I'd like two Erdbeer Becher"), **When** the assistant processes the request, **Then** the product card reflects the requested quantity pre-filled.

---

### User Story 2 - Ambiguous or Partial Product Match (Priority: P2)

A customer's spoken request partially matches multiple products — for example saying "something strawberry" when there are several strawberry-flavored items in the catalog. The assistant presents a short list of candidate products so the customer can pick the right one.

**Why this priority**: Without disambiguation, the assistant either guesses wrong or fails silently. This story preserves usability when voice intent is imprecise.

**Independent Test**: Can be tested by submitting a voice query with a partial product name that matches more than one catalog item and verifying that a ranked list of up to 5 candidates is returned, each selectable for the basket.

**Acceptance Scenarios**:

1. **Given** a customer's voice query matches more than one product in the catalog, **When** the assistant processes the request, **Then** it returns a ranked list of up to 5 matching products, each with its product card.
2. **Given** a list of candidate products is shown, **When** the customer selects one, **Then** that product is added to the basket and the disambiguation list is dismissed.

---

### User Story 3 - No Match Graceful Response (Priority: P3)

A customer speaks a request that does not match any product in the merchant's catalog, or says something the assistant cannot interpret as a valid command. The assistant responds with a short, friendly message — for example "I didn't understand that request — try browsing the product screen" — without showing any product card or navigating the user away.

**Why this priority**: Failing silently or crashing degrades trust. A graceful fallback ensures the assistant remains useful even when it cannot satisfy the request.

**Independent Test**: Can be tested by submitting a voice query for a product that definitively does not exist in the catalog and verifying a human-readable "not found" message is returned with no product cards.

**Acceptance Scenarios**:

1. **Given** a customer's voice query does not match any product in the catalog, or the request cannot be understood, **When** the assistant processes the request, **Then** it returns a friendly conversational message — no product card is shown and no screen navigation occurs.
2. **Given** a no-match or unrecognized-command response is shown, **When** the customer views it, **Then** the message suggests browsing the product screen manually as an alternative.

---

### Edge Cases

- What happens when the voice transcript is too short or ambiguous to extract any product intent?
- How does the assistant behave when the merchant's product catalog is empty?
- What if the same product name exists in multiple branches of the same merchant?
- How does the system handle a quantity that exceeds available stock?
- What if the AI reasoning step takes too long — is there a timeout and fallback?
- What if the customer's language differs from the product catalog's language?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept the transcribed text output from the voice query step as input to the AI reasoning stage.
- **FR-002**: The system MUST use a locally-running AI reasoning model to extract product intent (product name, quantity, and any qualifiers) from the transcript without sending data to external AI services.
- **FR-003**: The system MUST query the merchant's product catalog using the extracted product intent to find matching items.
- **FR-004**: The existing voice query endpoint MUST be extended to return a polymorphic response: the response MUST include a type discriminator so the client knows whether it received a transcript, a product data payload, or a no-match message.
- **FR-004a**: When the AI reasoning identifies a product intent, the response MUST be of type "product result" and contain the product data fields required by the client to render a selectable product card inline in the AI assistant chat and invoke the basket add-flow.
- **FR-005**: The system MUST allow the customer to add any returned product directly to the basket from the assistant screen with a single interaction.
- **FR-006**: When a single product is matched with high confidence, the system MUST return it immediately without asking the customer to disambiguate.
- **FR-007**: When multiple products match, the system MUST return a ranked shortlist of up to 5 candidates, each presented as a selectable product card.
- **FR-008**: When no product matches or the request cannot be understood as a valid command, the system MUST return a friendly, conversational message (e.g., "I didn't understand that request — try browsing the product screen") and MUST NOT return any product card. Navigation actions are out of scope for v1.
- **FR-009**: The endpoint MUST respond synchronously — the client sends the transcript in the request and receives the complete product result (or no-match message) in the same response within 5 seconds under normal load.
- **FR-010**: The system MUST pre-fill the quantity field on the returned product card when a quantity is stated in the voice request.

### Key Entities

- **VoiceTranscript**: The text string produced by the voice transcription step; the starting input for this feature.
- **ProductIntent**: The structured representation extracted by AI reasoning from the transcript — includes product name/keywords, requested quantity, and any modifiers (size, flavour, etc.).
- **ProductMatch**: A product from the merchant's catalog that satisfies the ProductIntent; carries name, description, price, image, availability, and branch association.
- **AssistantResponse**: The polymorphic structured API response returned by `/v1/voice/query`. Includes a `type` discriminator field with one of: `TRANSCRIPT` (existing behaviour — raw text reply), `PRODUCT_RESULT` (one or more matched products shown as selectable cards inline in the AI assistant chat), or `NO_MATCH` (a friendly message when no product matches or the request is not understood). The client inspects the type and renders accordingly. Navigation UI actions are out of scope for v1.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The system returns a product result or a no-match response within 5 seconds of receiving the transcript for 95% of requests.
- **SC-002**: For queries naming a product by its exact catalog name, the correct product is returned as the top result in at least 90% of cases.
- **SC-003**: Customers can add a voice-identified product to their basket in a single tap — zero additional navigation steps required.
- **SC-004**: Ambiguous queries covering 2 or more catalog items consistently return a ranked list rather than an arbitrary single pick or a silent failure.
- **SC-005**: The API response for every matched product contains all data fields required by the client to render a complete product card — no required field is missing or null for 100% of successful matches.

## Assumptions

- Voice transcription via Whisper is already implemented and produces text output; this feature begins at the point where the transcript is available.
- The merchant's product catalog (name, description, price, image, availability) is already stored in the system and queryable.
- The locally-running AI model is pre-installed and reachable by the backend service; this feature does not cover model installation or lifecycle management.
- The product card UI component already exists in the AI assistant screen or can be directly reused from the product browsing screen without redesign.
- The basket add-flow is already implemented; this feature only needs to invoke it with the selected product, not rebuild it.
- Product matching is scoped to the merchant and branch context the customer is currently interacting with — cross-merchant search is out of scope.
- Multi-language support (where the transcript language differs from the catalog language) is out of scope for v1; catalog and query are assumed to share the same language.
- The quantity stated in a voice query is advisory; stock validation at basket-add time is handled by the existing basket flow.
