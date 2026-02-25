# Quickstart: Publish Versioned Contract Stubs

## Goal

Validate the planned stub publication flow locally before implementation is merged.

## Prerequisites

- JDK 25 available for Gradle toolchain execution
- Access to the repository with executable `./gradlew`
- Artifactory test repository URL and credentials available through environment variables or local Gradle properties

## Local Verification Steps

1. Confirm the current contract suite passes:

```bash
./gradlew contractTest
```

2. Confirm the project version that will seed publication:

```bash
./gradlew properties --no-daemon | rg "^version:"
```

3. Run the stub packaging task once implementation adds publication support:

```bash
./gradlew verifierStubsJar
```

4. Run the publication task against a non-production Artifactory target with injected credentials:

```bash
BASE_VERSION="$(./gradlew -q properties --property version | awk -F': ' '/^version:/ {print $2}' | tail -n1)" && \
STUB_VERSION="${BASE_VERSION}-local.1" && \
ARTIFACTORY_URL="<repo-url>" \
ARTIFACTORY_REPOSITORY="<repo-key>" \
ARTIFACTORY_USERNAME="<user>" \
ARTIFACTORY_PASSWORD="<password>" \
./gradlew --no-daemon contractTest verifierStubsJar publishStubsPublicationToArtifactoryRepository \
  -PartifactoryUrl="$ARTIFACTORY_URL" \
  -PartifactoryRepository="$ARTIFACTORY_REPOSITORY" \
  -PartifactoryUsername="$ARTIFACTORY_USERNAME" \
  -PartifactoryPassword="$ARTIFACTORY_PASSWORD" \
  -PstubVersion="$STUB_VERSION"
```

5. Verify the published artifact in Artifactory using the resolved coordinates and version emitted by the build.

## CI Validation Expectations

- The `test` job succeeds before publication starts.
- The publication job logs the resolved version and target repository.
- Failed verification, missing artifacts, or duplicate versions prevent publication and produce a clear reason.
