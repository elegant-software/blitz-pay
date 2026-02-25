# Tasks: Publish Versioned Contract Stubs

**Input**: Design documents from `/specs/003-upload-stubs-artifactory/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The feature spec did not request test-first delivery, so this task list includes implementation-time verification tasks rather than separate mandatory test-first tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Single project layout at repository root
- CI/CD automation in `.github/workflows/`
- Gradle build and publication configuration in `build.gradle.kts` and `gradle.properties`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare repository-owned publication inputs and execution helpers

- [ ] T001 Add Artifactory publication properties and placeholder comments in /Users/mehdi/MyProject/BlitzPay/gradle.properties
- [ ] T002 [P] Add inline GitHub Actions publication flow in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core publication infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Configure stub packaging and Maven/Artifactory publication coordinates in /Users/mehdi/MyProject/BlitzPay/build.gradle.kts
- [ ] T004 [P] Add CI-safe version resolution logic for stub artifacts in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T005 [P] Change the reusable test workflow inputs to run contract verification in /Users/mehdi/MyProject/BlitzPay/.github/workflows/test.yml
- [ ] T006 Wire the main pipeline to call the local publish helper after successful tests in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Publish verified stubs automatically (Priority: P1) 🎯 MVP

**Goal**: Automatically publish a verified stub artifact to Artifactory after a successful CI verification run

**Independent Test**: Trigger a successful pipeline run and confirm one retrievable stub artifact is published in Artifactory with the resolved version and expected coordinates

### Implementation for User Story 1

- [ ] T007 [US1] Generate the publishable stub artifact from the contract build in /Users/mehdi/MyProject/BlitzPay/build.gradle.kts
- [ ] T008 [US1] Implement the publish workflow step sequencing and artifact handoff in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T009 [US1] Implement authenticated Artifactory upload execution in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T010 [US1] Document required CI secrets and manual validation steps in /Users/mehdi/MyProject/BlitzPay/GITHUB_ACTIONS.md

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Prevent unverified or duplicate stub publication (Priority: P2)

**Goal**: Ensure only verified and uniquely versioned stub artifacts are published

**Independent Test**: Run one failing verification pipeline and one duplicate-version publish attempt, then confirm no conflicting artifact is published and the pipeline reports the block condition

### Implementation for User Story 2

- [ ] T011 [US2] Enforce publish preconditions for missing stub outputs and failed verification paths in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T012 [US2] Add immutable duplicate-version protection and non-release version suffix rules in /Users/mehdi/MyProject/BlitzPay/build.gradle.kts
- [ ] T013 [US2] Apply publish gating conditions for eligible branches and events in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T014 [US2] Document duplicate, skip, and failure behavior for consumers and maintainers in /Users/mehdi/MyProject/BlitzPay/specs/003-upload-stubs-artifactory/contracts/stub-publication.md

**Checkpoint**: At this point, User Stories 1 and 2 should both work independently

---

## Phase 5: User Story 3 - Make publication outcome visible to maintainers (Priority: P3)

**Goal**: Expose clear publication results, versions, and failure reasons in CI outputs

**Independent Test**: Review successful, skipped, and failed pipeline runs and confirm maintainers can identify the status, version, and repository location or blocking reason without inspecting raw job internals

### Implementation for User Story 3

- [ ] T015 [US3] Add publish summary output and step-level status reporting in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T016 [P] [US3] Emit structured success, skip, and failure messages from the publish workflow in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml
- [ ] T017 [US3] Capture published coordinates and trace metadata in the Gradle publication flow in /Users/mehdi/MyProject/BlitzPay/build.gradle.kts
- [ ] T018 [US3] Update operator-facing workflow guidance for publication outcomes in /Users/mehdi/MyProject/BlitzPay/.github/workflows/README.md

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and cross-story cleanup

- [ ] T019 [P] Align the feature quickstart with implemented commands and environment variables in /Users/mehdi/MyProject/BlitzPay/specs/003-upload-stubs-artifactory/quickstart.md
- [ ] T020 Run end-to-end publish validation and record any final plan-to-implementation adjustments in /Users/mehdi/MyProject/BlitzPay/specs/003-upload-stubs-artifactory/research.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion
- **User Story 2 (Phase 4)**: Depends on User Story 1 publication path being in place
- **User Story 3 (Phase 5)**: Depends on User Story 1 publication flow and benefits from User Story 2 outcome states
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Starts after Foundational - no dependency on other stories
- **User Story 2 (P2)**: Depends on US1 because duplicate and failed-run protections wrap the initial publish path
- **User Story 3 (P3)**: Depends on US1 and should include US2 outcome reporting states

### Within Each User Story

- Build and workflow wiring before documentation for that story
- Publication execution before duplicate/failure enforcement refinements
- Outcome reporting after the publish path exists

### Parallel Opportunities

- `T001` and `T002` can proceed in parallel
- `T004` and `T005` can proceed in parallel after `T003` is understood
- Within US3, `T016` can run in parallel with `T015`
- `T019` can run in parallel with final validation once implementation stabilizes

---

## Parallel Example: User Story 1

```bash
Task: "Generate the publishable stub artifact from the contract build in /Users/mehdi/MyProject/BlitzPay/build.gradle.kts"
Task: "Document required CI secrets and manual validation steps in /Users/mehdi/MyProject/BlitzPay/GITHUB_ACTIONS.md"
```

---

## Parallel Example: User Story 3

```bash
Task: "Add publish summary output and step-level status reporting in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml"
Task: "Emit structured success, skip, and failure messages from the publish workflow in /Users/mehdi/MyProject/BlitzPay/.github/workflows/ci-cd.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Stop and validate a successful publish run end-to-end

### Incremental Delivery

1. Build the publish path in US1
2. Add safety guards in US2
3. Add maintainability and observability in US3
4. Finish with quickstart and validation cleanup

### Parallel Team Strategy

1. One engineer completes Gradle publication wiring while another prepares CI helper scripts in Phases 1-2
2. After US1 lands, one engineer hardens duplicate/failure handling while another improves reporting and documentation

---

## Notes

- All tasks follow the required checklist format
- `[P]` tasks touch separate files and can be worked independently
- Story labels appear only in user story phases
- Suggested MVP scope is Phase 3 / User Story 1
