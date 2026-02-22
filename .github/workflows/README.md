# GitHub Actions Workflows

This directory contains reusable GitHub Actions workflows for the BlitzPay application.

---

## Release Management

BlitzPay uses a fully automated release management system built on GitHub Actions and shell scripts — no external versioning libraries.

### Auto-Labeling (`auto-label.yml`)

Triggered on every `pull_request` event (opened / synchronize / reopened).

The workflow inspects the files changed in the PR and the PR title/branch name, then applies one or more labels automatically using the `gh` CLI. All label metadata is read from **`.github/labels.yml`** — edit that file to add, remove, or reconfigure labels without touching the workflow.

**Labeling rules (configured in `.github/labels.yml`):**

| Label | Color | Trigger |
|---|---|---|
| `feature` | `#0E8A16` | Any file under `src/main/kotlin/**` |
| `bug-fix` | `#D93F0B` | PR title or branch contains `fix`, `bug`, or `hotfix` |
| `documentation` | `#0075CA` | `*.md` files or `docs/**` |
| `infrastructure` | `#E4E669` | `Dockerfile`, `docker-compose.yml`, `k8s/**`, `.github/**` |
| `dependencies` | `#0366D6` | `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/**` |
| `tests` | `#BFD4F2` | `src/test/**` |
| `config` | `#C5DEF5` | `src/main/resources/**` |
| `breaking-change` | `#B60205` | PR title or body contains `BREAKING CHANGE` or `!:` |

- Multiple labels are applied when multiple patterns match.
- Labels are created automatically if they do not already exist.

> **Customisation:** To change a label's color, icon, title, description or matching patterns, edit `.github/labels.yml`. No workflow changes are needed.

### Semantic Versioning (`.github/scripts/semver.sh`)

A standalone bash script that implements [Semantic Versioning](https://semver.org/) from scratch.

**How it works:**

1. Reads the latest Git tag (`v*`) to determine the current version (defaults to `0.0.0` if no tags exist).
2. Reads the `PR_LABELS` environment variable and looks up each label's `bump` value in **`.github/labels.yml`** to determine the bump type:
   - `breaking-change` → **MAJOR** bump (`1.2.3` → `2.0.0`)
   - `feature` → **MINOR** bump (`1.2.3` → `1.3.0`)
   - `bug-fix`, `tests`, `config`, `dependencies`, `documentation`, `infrastructure` → **PATCH** bump (`1.2.3` → `1.2.4`)
3. When multiple labels exist the **highest priority** wins (MAJOR > MINOR > PATCH).
4. For the very first release (no prior tags): features start at `0.1.0`, patches at `0.0.1`.
5. Outputs `new_version` and `bump_type` via `$GITHUB_OUTPUT`.

> **Customisation:** Change a label's version bump type by updating its `bump` field in `.github/labels.yml`.

### Release Notes Generation (`release.yml`)

Triggered when a PR is **merged into `main`**.

**Steps:**

1. Collects PR labels from the merged PR.
2. Runs `semver.sh` to compute the next version.
3. Generates Markdown release notes grouped by label category. Section headings (icon + title) are read dynamically from **`.github/labels.yml`** in the order labels appear there:
   - 💥 Breaking Changes
   - 🚀 Features
   - 🐛 Bug Fixes
   - 📚 Documentation
   - 🏗️ Infrastructure
   - �� Dependencies
   - ✅ Tests
   - ⚙️ Configuration
4. Includes metadata: date, PR link, contributor, and a Full Changelog diff link.
5. Updates the `version` property in `build.gradle.kts` and pushes a `chore: release vX.Y.Z [skip ci]` commit.
6. Creates an annotated Git tag (`vX.Y.Z`).
7. Creates a GitHub Release with the generated notes.

---

## Workflows

### 1. Test Workflow (`test.yml`)

A reusable workflow for running application tests with PostgreSQL database.

**Features:**
- Runs tests with PostgreSQL service container
- Configurable Java version
- Publishes test reports
- Uploads test results as artifacts

**Usage:**
```yaml
jobs:
  test:
    uses: ./.github/workflows/test.yml
    with:
      java-version: '21'
      gradle-args: '--info'
```

**Inputs:**
- `java-version` (optional, default: '21'): Java version to use
- `gradle-args` (optional): Additional Gradle arguments

### 2. Build Workflow (`build.yml`)

A reusable workflow for building the application and creating Docker images.

**Features:**
- Builds application with Gradle
- Creates and pushes Docker images to GitHub Container Registry
- Supports multi-platform builds
- Generates artifact attestation
- Uses Docker layer caching

**Usage:**
```yaml
jobs:
  build:
    uses: ./.github/workflows/build.yml
    with:
      java-version: '21'
      image-name: 'blitzpay'
      image-tag: 'latest'
    secrets: inherit
```

**Inputs:**
- `java-version` (optional, default: '21'): Java version to use
- `image-name` (optional, default: 'blitzpay'): Docker image name
- `image-tag` (optional, default: 'latest'): Docker image tag
- `registry` (optional, default: 'ghcr.io'): Container registry
- `gradle-args` (optional): Additional Gradle arguments

**Outputs:**
- `image-uri`: Full image URI
- `image-digest`: Image digest

### 3. CI/CD Pipeline (`ci-cd.yml`)

Main CI/CD pipeline that orchestrates all workflows.

**Features:**
- Automatic testing on push and pull requests
- Automatic deployment to dev on develop branch
- Automatic deployment to staging on main/master branch
- Manual deployment to production via workflow_dispatch
- Environment-specific configuration

**Triggers:**
- Push to main or develop branches
- Pull requests to main or develop branches
- Manual workflow dispatch with environment selection

## Setup Instructions

### 1. Repository Variables

Configure the following variables in your GitHub repository settings:

- `JAVA_VERSION`: Java version to use (e.g., `25`)
- `JAVA_DISTRIBUTION`: Java distribution to use (e.g., `temurin`)

### 2. Repository Secrets

Configure the following secrets in your GitHub repository for deployment:

- `KUBECONFIG_DEV`, `DATABASE_URL_DEV`, etc. (see deployment workflow docs)

## Troubleshooting

### Build Failures
- Check test results artifacts in the Actions tab
- Review build logs for Gradle errors
- Ensure all dependencies are available

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)
