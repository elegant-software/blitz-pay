# Implementation Plan: Fixture-Based Testing Policy

**Branch**: `004-fixture-test-policy` | **Date**: 2026-03-25 | **Spec**: [/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/spec.md](/Users/mehdi/MyProject/BlitzPay/specs/004-fixture-test-policy/spec.md)
**Input**: Feature specification from `/specs/004-fixture-test-policy/spec.md`

## Summary

Revise the existing invoice test fixture implementation into a repository-standard fixture pattern that separates reusable business sample data from assertions, supports small scenario variants without full duplication, and records the rule in `CONSTITUATION.md`. The implementation will build on the current Kotlin/Spring Boot test stack by standardizing resource-backed fixtures, typed access helpers, and reviewer-facing policy guidance rather than adding a new testing framework.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25  
**Primary Dependencies**: Spring Boot 4.0.4, Spring WebFlux Test, Spring Modulith, Jackson Kotlin module, Mockito Kotlin, JUnit 5, Testcontainers  
**Storage**: Test resource files under `src/test/resources/` and repository documentation in Markdown  
**Testing**: `./gradlew test` with JUnit Platform, `@WebFluxTest`, Spring Boot test support, Mockito Kotlin, Testcontainers for broader integration coverage  
**Target Platform**: JVM-based Spring Boot service built and tested with Gradle on local development and CI environments  
**Project Type**: Modular Spring Boot web service  
**Performance Goals**: Preserve fast local fixture loading, keep representative test updates localized to one canonical fixture per shared scenario, and avoid increasing the current test feedback loop beyond normal unit/web-slice execution expectations  
**Constraints**: Keep changes focused to test architecture and documentation, preserve existing module boundaries, avoid sensitive or environment-specific sample data, and support readable tests that do not hide intent behind overly abstract fixture helpers  
**Scale/Scope**: Initial rollout covers the existing invoice-related tests and repository-level policy, with a directory and naming scheme that can scale to additional test domains without another redesign

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is not present in this repository. For this feature, the constitution gate is derived from [AGENTS.md](/Users/mehdi/MyProject/BlitzPay/AGENTS.md) and [.specify/references/architecture_guidelines.md](/Users/mehdi/MyProject/BlitzPay/.specify/references/architecture_guidelines.md) until `CONSTITUATION.md` is added by implementation.

- **Focused change gate**: PASS. The feature is limited to test fixtures, test reuse patterns, and repository policy text.
- **Test coverage gate**: PASS. The feature directly revises automated tests and will require updated or added tests as part of implementation.
- **Documentation gate**: PASS. The feature explicitly includes repository policy documentation.
- **Modularity gate**: PASS. Planned changes stay inside test support/resources plus repository documentation and do not expand module APIs.
- **Safety gate**: PASS. No destructive migration, production data, or secret handling is required.

Post-design re-check: PASS. The design artifacts keep the work scoped to `src/test/`, test resources, and repository standards without introducing new runtime modules or external service dependencies.

## Project Structure

### Documentation (this feature)

```text
specs/004-fixture-test-policy/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── fixture-testing-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── kotlin/com/elegant/software/blitzpay/
│   └── resources/
└── test/
    ├── kotlin/com/elegant/software/blitzpay/
    │   ├── payments/invoice/
    │   ├── config/
    │   └── support/
    └── resources/
        └── testdata/

specs/
└── 004-fixture-test-policy/

AGENTS.md
CONSTITUATION.md
build.gradle.kts
Dockerfile
settings.gradle.kts
```

**Structure Decision**: Keep the existing single-project Gradle layout. Implement the feature entirely through `src/test/kotlin/.../support`, `src/test/resources/testdata`, selected invoice tests under `src/test/kotlin/.../payments/invoice`, and repository-level governance documents. No production source restructuring is needed.

## Complexity Tracking

No constitution violations or complexity exceptions are currently justified for this feature.
