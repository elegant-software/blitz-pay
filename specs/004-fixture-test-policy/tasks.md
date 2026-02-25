# Tasks: Fixture-Based Testing Policy

**Input**: Design documents from `/specs/004-fixture-test-policy/`
**Prerequisites**: [plan.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/plan.md), [spec.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/spec.md), [research.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/research.md), [data-model.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/data-model.md), [fixture-testing-contract.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/contracts/fixture-testing-contract.md)

**Tests**: This feature explicitly revises automated tests and repository testing policy, so test tasks are included where they validate each user story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Capture the current fixture baseline and create the policy file targets required by the plan.

- [X] T001 Review the current fixture-backed invoice tests in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/InvoiceControllerTest.kt` and `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/ZugferdInvoiceServiceTest.kt`
- [X] T002 Review the current fixture loader and canonical invoice fixture in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoader.kt` and `/Users/mehdi/MyProject/BlitzPay/src/test/resources/testdata/invoice-test-data.json`
- [X] T003 Create the repository constitution file at `/Users/mehdi/MyProject/BlitzPay/CONSTITUATION.md` using the planned governance scope for fixture-based testing

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared fixture structure and support model that every user story depends on.

**⚠️ CRITICAL**: No user story work should begin until this phase is complete.

- [X] T004 Define the repository fixture naming and directory convention in `/Users/mehdi/MyProject/BlitzPay/src/test/resources/testdata/README.md`
- [X] T005 Refactor `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoader.kt` into a reusable fixture catalog entry point that supports canonical scenarios and targeted variants
- [X] T006 Align the canonical invoice scenario structure in `/Users/mehdi/MyProject/BlitzPay/src/test/resources/testdata/invoice-test-data.json` with the shared scenario and expectations model from the contract
- [X] T007 Add focused loader coverage for fixture parsing and variant behavior in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoaderTest.kt`

**Checkpoint**: Shared fixture structure, naming, and support code are ready for story-level adoption.

---

## Phase 3: User Story 1 - Reuse Shared Test Data (Priority: P1) 🎯 MVP

**Goal**: Replace repeated business sample data in the existing invoice tests with shared canonical fixtures and explicit variants.

**Independent Test**: Revise the representative invoice tests to consume the shared fixture and confirm the same business scenario no longer embeds repeated literal sample data while still passing `./gradlew test`.

### Tests for User Story 1

- [X] T008 [P] [US1] Add or revise controller fixture-usage assertions in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/InvoiceControllerTest.kt`
- [X] T009 [P] [US1] Add or revise service fixture-usage and variant assertions in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/ZugferdInvoiceServiceTest.kt`

### Implementation for User Story 1

- [X] T010 [US1] Update `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/InvoiceControllerTest.kt` to consume the canonical fixture helpers instead of repeated inline business values
- [X] T011 [US1] Update `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/ZugferdInvoiceServiceTest.kt` to consume the canonical fixture helpers and use explicit fixture variants for optional sections and edge cases
- [X] T012 [US1] Add any missing scenario helper methods required by the revised invoice tests in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoader.kt`
- [X] T013 [US1] Run and stabilize the invoice-focused test slice through `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/`

**Checkpoint**: User Story 1 is complete when the invoice tests share canonical fixture data and no longer repeat the same business scenario inline.

---

## Phase 4: User Story 2 - Follow a Consistent Test Data Pattern (Priority: P2)

**Goal**: Make fixture layout, names, and allowed variation patterns obvious to maintainers and contributors.

**Independent Test**: Review the fixture files, loader entry points, and revised tests and confirm a contributor can discover where shared test data lives, how scenarios are named, and how variants should be expressed without external explanation.

### Tests for User Story 2

- [X] T014 [P] [US2] Add readability-oriented fixture catalog assertions in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoaderTest.kt`
- [X] T015 [P] [US2] Add a repository quickstart validation example in `/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/quickstart.md`

### Implementation for User Story 2

- [X] T016 [US2] Rename or restructure fixture resources under `/Users/mehdi/MyProject/BlitzPay/src/test/resources/testdata/` to follow the repository naming convention defined in the foundational phase
- [X] T017 [US2] Document fixture scenario and variant usage examples in `/Users/mehdi/MyProject/BlitzPay/src/test/resources/testdata/README.md`
- [X] T018 [US2] Update the fixture support entry points and naming in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoader.kt` to match the documented convention
- [X] T019 [US2] Align invoice test call sites with the final naming and structure pattern in `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/InvoiceControllerTest.kt` and `/Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/ZugferdInvoiceServiceTest.kt`

**Checkpoint**: User Story 2 is complete when fixture placement, scenario naming, and variant handling are consistent and self-explanatory.

---

## Phase 5: User Story 3 - Enforce Repository Policy (Priority: P3)

**Goal**: Make fixture-based testing a standing repository rule that contributors and reviewers can apply consistently.

**Independent Test**: Review `CONSTITUATION.md` and confirm it defines when shared fixtures are expected, when inline literals are acceptable, and how reviewers assess compliance.

### Tests for User Story 3

- [X] T020 [P] [US3] Add policy acceptance examples for compliant and non-compliant fixture usage in `/Users/mehdi/MyProject/BlitzPay/CONSTITUATION.md`
- [X] T021 [P] [US3] Cross-check the policy language against the feature contract in `/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/contracts/fixture-testing-contract.md`

### Implementation for User Story 3

- [X] T022 [US3] Write the fixture-based testing principles, exception rules, and reviewer guidance in `/Users/mehdi/MyProject/BlitzPay/CONSTITUATION.md`
- [X] T023 [US3] Update `/Users/mehdi/MyProject/BlitzPay/AGENTS.md` so agent-facing repository guidance references `CONSTITUATION.md` as the governing policy source for fixture-based testing

**Checkpoint**: User Story 3 is complete when repository policy is explicit enough to guide future contributions and review decisions.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, cleanup, and cross-story verification.

- [X] T024 [P] Run the full repository test command `./gradlew test` from `/Users/mehdi/MyProject/BlitzPay`
- [X] T025 Verify the quickstart expectations and update any stale wording in `/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/quickstart.md`
- [X] T026 Review changed files for consistency with the feature plan in `/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately
- **Foundational (Phase 2)**: Depends on Setup completion and blocks all user stories
- **User Story 1 (Phase 3)**: Starts after Foundational and is the MVP
- **User Story 2 (Phase 4)**: Starts after Foundational; should build on the shared fixture structure established earlier
- **User Story 3 (Phase 5)**: Can start after Foundational, but is safest after the fixture pattern is concrete from US1 and US2
- **Polish (Phase 6)**: Starts after all desired user stories are complete

### User Story Dependencies

- **US1**: Depends on foundational fixture catalog and loader refactor
- **US2**: Depends on foundational naming/structure decisions and should align the concrete pattern proven in US1
- **US3**: Depends on the final fixture pattern decisions from US1 and US2 so policy text matches actual repository practice

### Within Each User Story

- Test revisions happen before the corresponding implementation edits
- Fixture support changes happen before or alongside consuming test changes
- Documentation and policy text should reflect the final implemented fixture pattern, not an earlier draft

### Parallel Opportunities

- `T008` and `T009` can run in parallel because they target different test files
- `T014` and `T015` can run in parallel because one updates loader-oriented validation while the other updates feature quickstart guidance
- `T020` and `T021` can run in parallel because one edits repository policy examples and the other validates policy coverage against the contract
- `T024` and `T025` can run in parallel at the end if implementation is otherwise complete

---

## Parallel Example: User Story 1

```bash
Task: "Add or revise controller fixture-usage assertions in /Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/InvoiceControllerTest.kt"
Task: "Add or revise service fixture-usage and variant assertions in /Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/ZugferdInvoiceServiceTest.kt"
```

## Parallel Example: User Story 2

```bash
Task: "Add readability-oriented fixture catalog assertions in /Users/mehdi/MyProject/BlitzPay/src/test/kotlin/com/elegant/software/blitzpay/support/TestFixtureLoaderTest.kt"
Task: "Add a repository quickstart validation example in /Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/quickstart.md"
```

## Parallel Example: User Story 3

```bash
Task: "Add policy acceptance examples for compliant and non-compliant fixture usage in /Users/mehdi/MyProject/BlitzPay/CONSTITUATION.md"
Task: "Cross-check the policy language against the feature contract in /Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/contracts/fixture-testing-contract.md"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup
2. Complete Foundational
3. Complete User Story 1
4. Validate the invoice tests independently

### Incremental Delivery

1. Establish the shared fixture structure and loader
2. Deliver shared fixture reuse in invoice tests
3. Standardize naming and variant structure for contributors and reviewers
4. Encode the final standard in `CONSTITUATION.md`

### Parallel Team Strategy

1. One contributor stabilizes foundational fixture support
2. One contributor revises invoice tests for shared reuse
3. One contributor prepares governance and quickstart documentation once the concrete pattern is settled

## Notes

- All tasks follow the required checklist format with IDs and file paths
- User story tasks include `[US1]`, `[US2]`, or `[US3]` labels
- `[P]` markers are used only where files and dependencies permit parallel work
- The suggested MVP scope is User Story 1 after Setup and Foundational phases
