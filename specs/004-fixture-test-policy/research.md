# Research: Fixture-Based Testing Policy

**Feature**: [/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/spec.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/spec.md)  
**Date**: 2026-03-25

## Decision 1: Use resource-backed named fixtures as the canonical source for shared business scenarios

**Decision**: Store reusable business scenarios as named fixture documents under `src/test/resources/testdata/` and load them through typed support code in `src/test/kotlin/com/elegant/software/blitzpay/support/`.

**Rationale**: Current repository work already loads invoice sample data from test resources, which aligns with the user’s goal of avoiding repeated hard-coded business text. JUnit’s current parameterized-test guidance favors reusable argument sources such as `@MethodSource` and `@FieldSource`, which fit naturally with shared fixture catalogs rather than repeated literals in each test. This approach also keeps domain examples readable to reviewers because the canonical sample data remains visible in a dedicated resource file.

**Alternatives considered**:
- Keep literals inline in each test: rejected because it preserves duplication and makes shared scenario updates expensive.
- Use builders only with all data defined in code: rejected because builders help variation but still encourage repeated business strings unless backed by canonical scenario data.
- Generate randomized data only: rejected because randomized values are poor for reviewer comprehension and golden-document assertions.

## Decision 2: Separate canonical input data from expected outcomes in the fixture schema

**Decision**: Model each reusable fixture around a canonical scenario with explicit input data and explicit expected assertions, rather than mixing ad hoc assertions directly into test code.

**Rationale**: The existing invoice fixture already follows this basic split through request data plus expectations. Retaining and tightening that separation reduces repetition in assertions while keeping the fixture understandable. It also gives reviewers a stable place to inspect what makes a scenario valid without diffing through multiple tests.

**Alternatives considered**:
- Store only input payloads and leave all expectations inline: rejected because it leaves assertion duplication unresolved.
- Store only expected outputs: rejected because tests still need repeated setup input and lose scenario completeness.

## Decision 3: Prefer code-level variants derived from a shared fixture over duplicating near-identical fixture files

**Decision**: Use a shared base fixture for stable scenarios and express small edge-case differences as narrowly scoped variants in test code or dedicated helper methods, only creating a new fixture file when the business scenario is meaningfully distinct.

**Rationale**: Current JUnit guidance supports reusable data providers and named argument sources; combining a base fixture with explicit variants keeps tests expressive without proliferating nearly identical files. This also matches the user’s request for reuse and avoidance of repeated data while preserving test readability.

**Alternatives considered**:
- Create a separate full fixture file for every variation: rejected because it scales poorly and hides what actually changed.
- Force every variation to be inline: rejected because it weakens consistency and reviewability.

## Decision 4: Keep test-style responsibilities aligned with the current stack

**Decision**: Continue using typed domain objects and shared fixtures for service and controller tests, while keeping Spring WebFlux slice tests responsible for protocol behavior and service tests responsible for document/content behavior.

**Rationale**: Spring’s testing guidance emphasizes `WebTestClient` for WebFlux application testing, and the repository already uses `@WebFluxTest` for controller coverage. The fixture standard should support this split instead of collapsing all tests into one style. Shared fixtures should be reusable across both service-level and controller-level tests, but each test layer should keep its current responsibility boundaries.

**Alternatives considered**:
- Convert all tests to full integration tests: rejected because it would slow the suite and broaden scope beyond fixture policy.
- Use fixtures only in controller tests: rejected because service tests also contain repeated business data and benefit from the same canonical scenarios.

## Decision 5: Treat the fixture standard as repository governance, not just a local helper

**Decision**: Add fixture-based testing rules to `CONSTITUATION.md`, including when shared fixtures are expected, when inline literals are acceptable, and how reviewers should assess compliance.

**Rationale**: The user explicitly asked for fixture usage to become a standard policy. Without repository governance, fixture support code alone will not prevent future regression into repeated literals.

**Alternatives considered**:
- Leave guidance only in code comments: rejected because it is too local and easy to miss during reviews.
- Document only in the feature spec: rejected because the spec is feature-scoped, not a standing repository rule.

## Decision 6: Scope the initial implementation to the existing invoice fixture path and evolve structure incrementally

**Decision**: Start by revising the existing invoice-focused fixture implementation and introduce a scalable naming and directory pattern that additional test domains can follow later.

**Rationale**: This keeps the change minimal, reviewable, and aligned with the repository’s current branch state. It satisfies the spec’s initial scope while leaving room for expansion without another policy redesign.

**Alternatives considered**:
- Migrate every existing test domain immediately: rejected because the repository currently has only a small number of explicit fixture-backed tests and the feature does not require a broad refactor.
- Freeze the existing flat path forever: rejected because the user explicitly wants structure and repeatable patterns, not a one-off loader.

## Sources Consulted

- JUnit User Guide, current parameterized tests and argument sources: https://docs.junit.org/current/writing-tests/parameterized-classes-and-tests/
- Spring Framework testing reference for WebFlux and `WebTestClient`: https://docs.spring.io/spring-framework/reference/web/webmvc-test.html
- Testcontainers lifecycle guidance for shared test infrastructure patterns: https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/
