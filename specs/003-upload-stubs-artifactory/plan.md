# Implementation Plan: Publish Versioned Contract Stubs

**Branch**: `003-upload-stubs-artifactory` | **Date**: 2026-03-24 | **Spec**: [spec.md](/Users/mehdi/MyProject/BlitzPay/specs/003-upload-stubs-artifactory/spec.md)
**Input**: Feature specification from `/specs/003-upload-stubs-artifactory/spec.md`

## Summary

Publish verified contract stubs to Artifactory only after the CI test stage succeeds by adding a repository-owned publish step, generating a uniquely versioned stub artifact from the Gradle project version, and exposing clear publication status for maintainers and downstream consumers.

## Technical Context

**Language/Version**: Kotlin 2.3.20 on Java 25, Gradle Kotlin DSL, Spring Boot 4.0.4  
**Primary Dependencies**: Spring Boot WebFlux/Data JPA, Spring Modulith, JUnit 5, Spring Boot test support, GitHub Actions reusable workflows  
**Storage**: Artifactory for published stub artifacts; no new application data store  
**Testing**: `test`, custom `contractTest`, CI test workflow in GitHub Actions, artifact publication verification in CI  
**Target Platform**: GitHub Actions on Linux runners for delivery; Spring Boot backend service for source artifact production  
**Project Type**: Web service with CI/CD automation and external contract consumers  
**Performance Goals**: Successful CI runs publish one retrievable stub artifact within 5 minutes of test completion; maintainers can identify the publication outcome within 2 minutes  
**Constraints**: No stub publication on failed verification, no conflicting duplicate versions, no secret exposure in workflow logs, minimal coupling to the external reusable test workflow, preserve current release/versioning flow  
**Scale/Scope**: One backend service, one contract stub artifact stream, local workflow changes under `.github/workflows`, Gradle publication wiring, and Artifactory credential/configuration handling

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Secrets and Key Material Are Never Exposed**: Pass. Plan uses GitHub Actions secrets for Artifactory credentials and requires redacted logging of repository credentials and tokens.
- **II. Payment Requests Must Be Cryptographically Verifiable**: Pass. The feature does not alter payment signing behavior, but it distributes artifacts representing verified request/response contracts, so publication remains gated on successful contract verification.
- **III. Every Behavior Change Requires Automated Verification**: Pass. The plan adds CI verification and publication checks and keeps contract publication dependent on successful automated test execution.
- **IV. Contracts and Failure Modes Must Be Explicit**: Pass. The design documents artifact coordinates, versioning rules, duplicate-version handling, missing-artifact behavior, and repository-unavailable failure handling.
- **V. Production Behavior Must Be Observable**: Pass. The pipeline will surface published version, target repository, skip reasons, and failure reasons in workflow output.

**Post-Design Re-check**: Pass. Phase 1 artifacts preserve secret isolation, define artifact and workflow contracts explicitly, and keep publication observable and test-gated.

## Project Structure

### Documentation (this feature)

```text
specs/003-upload-stubs-artifactory/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── stub-publication.md
└── tasks.md
```

### Source Code (repository root)

```text
.github/
├── scripts/
│   └── semver.sh
└── workflows/
    ├── ci-cd.yml
    ├── test.yml
    └── build.yml

src/
├── contractTest/
│   ├── kotlin/com/elegant/software/blitzpay/contract/
│   └── resources/contracts/
├── main/
│   ├── kotlin/com/elegant/software/blitzpay/
│   └── resources/
└── test/
    └── kotlin/com/elegant/software/blitzpay/

build.gradle.kts
gradle/libs.versions.toml
```

**Structure Decision**: Keep the existing single-service Gradle structure. Implementation work belongs in Gradle build configuration and GitHub Actions workflows, with contract source assets remaining under `src/contractTest/resources/contracts`.

## Phase 0 Research Outcomes

- Use the Gradle `project.version` value as the canonical version base for stub publishing because repository release automation updates that version from git-tag-driven semantic versioning.
- Publish from a repository-owned follow-on CI job after the reusable test workflow succeeds rather than modifying the shared external workflow first; this minimizes coupling and keeps publication logic local.
- For non-release CI runs, require a unique CI-derived suffix or restrict publish triggers to flows that guarantee unique versions; final implementation should choose the repository’s preferred rule but may not publish duplicate bare `project.version` values from branch/PR runs.
- Regenerate or package the stub artifact in the local publish job if the external reusable workflow does not expose build outputs.

## Phase 1 Design Direction

- Extend Gradle so the stub artifact can be packaged predictably and published with repository-managed coordinates.
- Add a local CI publication step that depends on successful test execution and emits explicit status details.
- Define Artifactory secrets and repository settings as CI inputs rather than hardcoded build configuration.
- Verify duplicate-version behavior, missing-stub behavior, and repository outage handling in the publication path.

## Complexity Tracking

No constitution violations or exceptional complexity justifications are required for this feature.
