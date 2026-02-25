# Contract: Stub Publication Interface

## Purpose

Define the externally visible contract for publishing verified BlitzPay contract stubs to Artifactory so downstream consumers and maintainers have a stable expectation for when artifacts appear and how they are identified.

## Trigger Contract

- Publication starts only after the repository test stage reports success for the same commit.
- Publication must not run when contract verification failed, required stub output is missing, or version resolution is invalid.
- Pull request publication is skipped by default.
- Branch and manual flows may publish only if the resolved version is unique for that run.

## Artifact Identity Contract

- **Group**: Service-owned namespace managed by this repository.
- **Artifact**: Contract stub package for BlitzPay consumers.
- **Version**:
  - Release flows use the canonical project version.
  - Tag-driven release flows use the git tag without the leading `v`.
  - Non-release flows use a deterministic CI-unique derivative of the project version.
- **Metadata**:
  - Source commit SHA
  - Pipeline run identifier
  - Publication timestamp
  - Verification status marker

## Repository Contract

- Target repository is Artifactory and is configured through CI secrets or variables, not hardcoded values.
- Credentials must be injected only at runtime.
- Publication must fail closed if repository configuration or credentials are missing.

## Consumer Contract

- Consumers can retrieve the most recently published stub package for a known version from Artifactory.
- Published artifacts must remain immutable once a version is accepted.
- Duplicate publish attempts for the same immutable version must produce a clear non-success result rather than replacing the existing artifact.

## Observability Contract

- Successful runs report the published version and repository location.
- Skipped runs report the reason publication did not occur.
- Failed runs report the stage and reason of failure without disclosing secrets.
