# Tasks: Voice Query and Spoken Response

**Input**: Design documents from `specs/010-voice-transcription-response/`  
**Branch**: `010-voice-transcription-response`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

---

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on sibling tasks)
- **[US1]**: User Story 1 — Send voice and receive spoken answer
- **[US2]**: User Story 2 — Handle poor audio gracefully
- **[US3]**: User Story 3 — Contextual domain responses

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the voice feature scaffolding and environment-driven configuration points used by all stories.

- [X] T001 Add the Spring AI/OpenAI client dependency for the voice module in `build.gradle.kts`
- [X] T002 Add `blitzpay.voice.*` configuration keys and environment placeholders to `src/main/resources/application.yml`
- [X] T003 Create the `voice` package skeleton and module metadata in `src/main/kotlin/com/elegant/software/blitzpay/voice/package-info.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/api/package-info.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the shared voice contracts, configuration, and contract-test wiring required before any user story implementation can proceed.

**⚠️ CRITICAL**: No user story work should start until this phase is complete.

- [X] T004 Create `VoiceProperties.kt` with `@ConfigurationProperties(prefix = "blitzpay.voice")` in `src/main/kotlin/com/elegant/software/blitzpay/voice/config/VoiceProperties.kt`
- [X] T005 [P] Create shared in-flight models and error types in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceModels.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceExceptions.kt`
- [X] T006 [P] Create provider-facing ports in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/SpeechTranscriptionClient.kt`, `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceReasoningClient.kt`, and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/SpeechSynthesisClient.kt`
- [X] T007 Create `VoiceGateway.kt` and a basic `VoiceConfiguration.kt` bean wiring shell in `src/main/kotlin/com/elegant/software/blitzpay/voice/api/VoiceGateway.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/config/VoiceConfiguration.kt`
- [X] T008 Extend contract-test wiring for the future voice controller contract suite in `src/contractTest/kotlin/com/elegant/software/blitzpay/contract/ContractVerifierBase.kt` and `src/contractTest/kotlin/com/elegant/software/blitzpay/support/ContractTestConfig.kt`

**Checkpoint**: Shared voice infrastructure is ready; user story work can now begin.

---

## Phase 3: User Story 1 - Send Voice and Receive Spoken Answer (Priority: P1) 🎯 MVP

**Goal**: Accept an authenticated voice upload and return a playable spoken answer in one synchronous `/v1/voice/query` call.

**Independent Test**: Submit a valid authenticated MP4 or MP3 sample to `POST /v1/voice/query` and verify the endpoint returns `200 OK` with `audio/mpeg` and meaningful synthesized speech within the target flow.

### Tests for User Story 1

- [X] T009 [P] [US1] Create controller and service unit tests for the happy path in `src/test/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryControllerTest.kt` and `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryServiceTest.kt`
- [X] T010 [P] [US1] Create contract tests for the authenticated success path and auth rejection in `src/contractTest/kotlin/com/elegant/software/blitzpay/voice/VoiceQueryControllerContractTest.kt`

### Implementation for User Story 1

- [X] T011 [P] [US1] Implement bearer-token subject extraction and multipart upload parsing in `src/main/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryController.kt`
- [X] T012 [P] [US1] Implement prompt construction for supported BlitzPay topics in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoicePromptBuilder.kt`
- [X] T013 [US1] Implement the core orchestration flow in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryService.kt`
- [X] T014 [P] [US1] Implement the provider adapters in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/SpeechTranscriptionClient.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/SpeechSynthesisClient.kt`
- [X] T015 [US1] Wire the provider adapters and expose `POST /v1/voice/query` in `src/main/kotlin/com/elegant/software/blitzpay/voice/config/VoiceConfiguration.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryController.kt`

**Checkpoint**: User Story 1 should now be fully functional and independently testable.

---

## Phase 4: User Story 2 - Handle Poor Audio Gracefully (Priority: P2)

**Goal**: Reject unusable audio and transient provider failures with stable, actionable RFC 9457 problem responses.

**Independent Test**: Submit missing, oversized, unsupported, silent, too-short, and provider-failure audio cases to `POST /v1/voice/query` and verify the response status and `reason` code match the contract.

### Tests for User Story 2

- [X] T016 [P] [US2] Add controller validation tests for missing audio, unsupported media type, and oversized uploads in `src/test/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryControllerTest.kt`
- [X] T017 [P] [US2] Add service-level error mapping tests for too-short audio, no speech detected, too-long audio, and upstream failures in `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryServiceTest.kt`
- [X] T018 [P] [US2] Extend the voice contract suite for all error scenarios in `src/contractTest/kotlin/com/elegant/software/blitzpay/voice/VoiceQueryControllerContractTest.kt`

### Implementation for User Story 2

- [X] T019 [US2] Add multipart size, content-type, and missing-part validation to `src/main/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryController.kt`
- [X] T020 [US2] Implement `ProblemDetail` mapping for voice-specific failures in `src/main/kotlin/com/elegant/software/blitzpay/voice/web/VoiceQueryController.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceExceptions.kt`
- [X] T021 [US2] Enforce duration and no-speech guards inside the orchestration flow in `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryService.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/SpeechTranscriptionClient.kt`

**Checkpoint**: User Story 2 should now be independently testable with stable failure semantics.

---

## Phase 5: User Story 3 - Contextual Domain Responses (Priority: P3)

**Goal**: Use authenticated BlitzPay payment context for domain-specific voice answers and politely redirect unsupported topics.

**Independent Test**: Submit a voice query like “what is my latest payment?” for a user with known payment history and verify the spoken answer reflects that user’s recent payment summary; submit an out-of-domain query and verify the reply redirects to supported BlitzPay topics.

### Tests for User Story 3

- [X] T022 [P] [US3] Add payment-context service tests in `src/test/kotlin/com/elegant/software/blitzpay/payments/push/PaymentVoiceContextServiceTest.kt`
- [X] T023 [P] [US3] Add prompt-building and contextual-answer tests in `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/VoicePromptBuilderTest.kt` and `src/test/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryServiceTest.kt`
- [X] T024 [P] [US3] Extend the voice contract suite for contextual-payment and unsupported-topic scenarios in `src/contractTest/kotlin/com/elegant/software/blitzpay/voice/VoiceQueryControllerContractTest.kt`

### Implementation for User Story 3

- [X] T025 [US3] Add a Liquibase migration for payment voice context columns and register it in `src/main/resources/db/changelog/20260425-001-push-payment-voice-context.sql` and `src/main/resources/db/changelog/db.changelog-master.yaml`
- [X] T026 [US3] Extend payment request and status persistence with caller subject and order summary fields in `src/main/kotlin/com/elegant/software/blitzpay/payments/truelayer/api/PaymentGateway.kt`, `src/main/kotlin/com/elegant/software/blitzpay/payments/qrpay/PaymentRequestController.kt`, `src/main/kotlin/com/elegant/software/blitzpay/payments/truelayer/outbound/PaymentService.kt`, and `src/main/kotlin/com/elegant/software/blitzpay/payments/push/persistence/PaymentStatusEntity.kt`
- [X] T027 [US3] Add recent-payment query contracts in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/RecentPaymentSummary.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/PaymentVoiceContextGateway.kt`
- [X] T028 [US3] Implement the payment voice context service and repository queries in `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentVoiceContextService.kt` and `src/main/kotlin/com/elegant/software/blitzpay/payments/push/internal/PaymentStatusService.kt`
- [X] T029 [US3] Integrate payment context retrieval and unsupported-topic redirection into `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoicePromptBuilder.kt` and `src/main/kotlin/com/elegant/software/blitzpay/voice/internal/VoiceQueryService.kt`

**Checkpoint**: User Story 3 should now be independently testable with factual recent-payment answers and safe topic redirection.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Finish verification, repository metadata, and documentation that affect multiple stories.

- [X] T030 [P] Update feature documentation and required voice environment variables in `README.md` and `AGENTS.md`
- [X] T031 Verify the new module passes modulith verification in `src/test/kotlin/com/elegant/software/blitzpay/ModularityTest.kt` and `src/test/kotlin/com/elegant/software/blitzpay/merchant/MerchantModularityTest.kt`
- [X] T032 Run the full verification suite with `./gradlew check` and record validation notes in `specs/010-voice-transcription-response/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies, can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 and blocks all user stories.
- **Phase 3 (US1)**: Depends on Phase 2 and delivers the MVP.
- **Phase 4 (US2)**: Depends on Phase 3 because it hardens the same endpoint and service path.
- **Phase 5 (US3)**: Depends on Phase 3 and can proceed after the core voice flow exists; it does not require US2 to be complete.
- **Phase 6 (Polish)**: Depends on the desired user stories being complete.

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational and has no dependency on other user stories.
- **US2 (P2)**: Builds on the US1 endpoint and service path but remains independently testable once implemented.
- **US3 (P3)**: Builds on the US1 endpoint and voice pipeline plus new payment-context persistence/query support.

### Within Each User Story

- Tests should be written before or alongside implementation and must fail before the corresponding implementation is considered complete.
- Controller contract tests and unit tests come before final wiring.
- Shared models and ports come before orchestration.
- Persistence changes come before contextual prompt integration.

---

## Parallel Opportunities

### Phase 2

```bash
T005: Create VoiceModels.kt and VoiceExceptions.kt
T006: Create speech/reasoning/synthesis client ports
```

### User Story 1

```bash
T009: Create controller and service unit tests
T010: Create contract tests for the success path
T012: Implement VoicePromptBuilder.kt
T014: Implement provider adapters
```

### User Story 2

```bash
T016: Add controller validation tests
T017: Add service-level error mapping tests
T018: Extend contract tests for error scenarios
```

### User Story 3

```bash
T022: Add payment-context service tests
T023: Add prompt-building and contextual-answer tests
T027: Add RecentPaymentSummary.kt and PaymentVoiceContextGateway.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1.
4. Stop and validate the authenticated voice-in/audio-out path independently.

### Incremental Delivery

1. Deliver US1 for the end-to-end voice experience.
2. Add US2 to make failure handling production-safe without changing the happy-path contract.
3. Add US3 to bring real BlitzPay payment context into responses.
4. Finish with documentation and full verification.

### Parallel Team Strategy

1. One developer completes Phase 1 and Phase 2.
2. After that:
   - Developer A can drive US1.
   - Developer B can prepare US2 tests and error handling.
   - Developer C can prepare US3 payment-context tests and API contracts.

---

## Notes

- All task lines follow the required checklist format with task ID, optional `[P]`, optional story label, and explicit file paths.
- `POST /v1/voice/query` remains the only public HTTP endpoint for this feature in v1.
- Payment context stays owned by `payments.push`; the `voice` module must consume only `payments.push.api` contracts.
