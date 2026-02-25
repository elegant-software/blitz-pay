# Data Model: Publish Versioned Contract Stubs

## StubPublicationRequest

- **Purpose**: Represents the inputs needed to publish a verified contract stub artifact.
- **Fields**:
  - `serviceName`: Service identifier used in artifact coordinates.
  - `groupId`: Artifact group namespace.
  - `artifactId`: Stub artifact name.
  - `baseVersion`: Canonical version from the Gradle project.
  - `resolvedVersion`: Final publish version after applying release or CI uniqueness rules.
  - `repositoryKey`: Target Artifactory repository identifier.
  - `repositoryUrl`: Target repository endpoint.
  - `triggerType`: Source of the pipeline run such as branch push, pull request, or release flow.
  - `commitSha`: Immutable source revision used for traceability.
  - `runId`: CI run identifier used for uniqueness and auditability.
- **Validation Rules**:
  - `resolvedVersion` must be non-empty and unique for the target repository.
  - `repositoryUrl` and `repositoryKey` must be present before publication starts.
  - Credentials are not part of the request model and must be provided through secret injection only.

## StubArtifact

- **Purpose**: Represents the generated deliverable consumed by downstream teams.
- **Fields**:
  - `coordinates`: Combined group, artifact, and version identifier.
  - `packaging`: Artifact packaging type used by the repository.
  - `sourceCommit`: Commit tied to the artifact.
  - `generatedAt`: Publication timestamp.
  - `verificationStatus`: Verification state for the artifact source run.
  - `location`: Artifact repository path or URI.
- **Validation Rules**:
  - `verificationStatus` must be `verified` before publication.
  - `location` must be recorded on successful publication.

## PublicationResult

- **Purpose**: Captures the outcome of a publication attempt for maintainers and automation.
- **Fields**:
  - `status`: `published`, `skipped`, or `failed`.
  - `reason`: Human-readable explanation for skipped or failed outcomes.
  - `publishedVersion`: Final artifact version when publication succeeds.
  - `repositoryLocation`: Artifact path or repository reference when publication succeeds.
  - `attemptedAt`: Timestamp of the attempt.
- **Validation Rules**:
  - `reason` is required when `status` is `skipped` or `failed`.
  - `publishedVersion` and `repositoryLocation` are required when `status` is `published`.

## Relationships

- One `StubPublicationRequest` produces zero or one `StubArtifact`.
- Each `StubPublicationRequest` results in exactly one `PublicationResult`.
- A `PublicationResult` references the `StubArtifact` only when status is `published`.

## State Transitions

- `pending` -> `validated`: Publication inputs are complete and version rules are satisfied.
- `validated` -> `published`: Verification passed and the repository accepted the artifact.
- `validated` -> `skipped`: Verification failed, artifact output is missing, publish trigger is ineligible, or version resolution is invalid.
- `validated` -> `failed`: Repository communication or publication execution failed after validation began.
