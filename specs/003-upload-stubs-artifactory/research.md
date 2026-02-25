# Research: Publish Versioned Contract Stubs

## Decision 1: Use repository-owned post-test publication instead of modifying the shared reusable workflow first

- **Decision**: Add a local follow-on CI job in this repository that runs only after the reusable test workflow succeeds and handles stub packaging and Artifactory publication.
- **Rationale**: `.github/workflows/test.yml` delegates execution to `medimohammadise/elegant-ci-cd-pipeline/.github/workflows/back-end-gradle-test.yml@003-gradle-test-workflow`, and the local repo has no visible artifact handoff contract from that workflow. A local follow-on job keeps the feature self-contained and avoids blocking on changes to shared infrastructure.
- **Alternatives considered**:
  - Modify the external reusable workflow directly. Rejected because the repository does not control that implementation here and planning would depend on out-of-repo coordination.
  - Publish inside the reusable test job only. Rejected because publication logic, secrets, and failure reporting would be less visible and harder to evolve from this repository.

## Decision 2: Use Gradle project version as the canonical version base

- **Decision**: Derive the stub artifact version from `project.version` in `build.gradle.kts`, which currently aligns with the repository’s release/tag flow.
- **Rationale**: Repository release automation computes a semantic version from git tags and PR labels via `.github/scripts/semver.sh`, then updates `build.gradle.kts` according to `.github/workflows/README.md`. That makes `project.version` the most practical canonical value available to Gradle publication code.
- **Alternatives considered**:
  - Use docker image tags. Rejected because image tags are build metadata, not the canonical Gradle artifact version.
  - Use git tags only. Rejected because regular CI runs do not expose a tag-derived version into the local test workflow.

## Decision 3: Avoid duplicate versions by requiring a unique CI-safe version rule for non-release publishes

- **Decision**: The implementation must not publish branch or pull-request builds using a bare static `project.version`. For non-release publishes, use a deterministic CI suffix or limit publishing to flows that guarantee unique versions.
- **Rationale**: The current in-repo version is static between releases. Publishing bare `0.2.2` repeatedly from multiple successful CI runs would create conflicts or ambiguous artifacts in Artifactory.
- **Alternatives considered**:
  - Always overwrite the same version. Rejected because the spec requires avoiding conflicting duplicates and preserving consumer trust.
  - Publish only from failed-over manual processes. Rejected because the feature goal is automated post-verification publication.

## Decision 4: Recreate or package the stub artifact in the publish job if the reusable workflow does not expose outputs

- **Decision**: Design the local publish job so it can generate or package the stubs itself after the upstream test job passes.
- **Rationale**: There is no local evidence that the reusable workflow uploads or exposes the generated stub artifact. Re-packaging in the publish job is more reliable than assuming artifact inheritance across reusable workflow boundaries.
- **Alternatives considered**:
  - Depend on artifact outputs from the reusable workflow. Rejected for now because no such output contract is visible in this repository.
  - Skip local packaging and publish directly from developer machines. Rejected because the feature explicitly targets automated CI publication.

## Implementation Notes

- The repository now uses the Spring Cloud Contract Gradle plugin to expose `verifierStubsJar` and `publishStubsPublicationToArtifactoryRepository`.
- `test.yml` runs `contractTest` as the integration verification stage.
- `ci-cd.yml` adds a repository-owned `publish-stubs` job that runs after successful tests on non-PR events.
- The publish helper validates required Artifactory configuration, resolves a CI-safe version, reruns contract verification, and writes success or failure details to the GitHub Actions step summary.
