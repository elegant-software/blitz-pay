#!/usr/bin/env bash
# setup-github-env.sh
#
# Creates all required GitHub Actions repository variables and secrets.
# Uses the GitHub CLI (gh). Run once when bootstrapping a new repo or fork.
#
# Usage:
#   .github/scripts/setup-github-env.sh            # set both vars and secrets
#   .github/scripts/setup-github-env.sh --vars      # variables only
#   .github/scripts/setup-github-env.sh --secrets   # secrets only
#
# Prerequisites:
#   - gh CLI installed and authenticated (gh auth login)
#   - Run from the root of the repository
#
# Note: Kubernetes secrets (K8S_SERVER, K8S_CA_CERT, K8S_TOKEN) are managed
# at the organization level — see setup-k8s-org-secrets.sh instead.
#
# Update this file whenever a new var or secret is added to a workflow.
# See CONTRIBUTING.md for instructions.

set -euo pipefail

# ─── ARGUMENT PARSING ────────────────────────────────────────────────────────

RUN_VARS=true
RUN_SECRETS=true

for arg in "$@"; do
  case $arg in
    --vars)    RUN_SECRETS=false ;;
    --secrets) RUN_VARS=false    ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: $0 [--vars | --secrets]"
      exit 1
      ;;
  esac
done

# ─── SHARED HELPERS ──────────────────────────────────────────────────────────

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

set_var() {
  local name="$1"
  local value="$2"
  # Skip if the variable already exists
  if gh variable get "$name" --repo "$REPO" &>/dev/null; then
    echo "  [var]    $name — already set, skipping"
    return
  fi
  echo "  [var]    $name = $value"
  gh variable set "$name" --body "$value" --repo "$REPO"
}

set_secret() {
  local name="$1"
  local description="$2"
  local optional="${3:-false}"

  if [ "$optional" = "true" ]; then
    read -r -p "  [secret] $name ($description) [leave blank to skip]: " value
    if [ -z "$value" ]; then
      echo "           Skipped."
      return
    fi
  else
    read -r -p "  [secret] $name ($description): " value
    while [ -z "$value" ]; do
      echo "           This secret is required."
      read -r -p "  [secret] $name ($description): " value
    done
  fi

  gh secret set "$name" --body "$value" --repo "$REPO"
  echo "           Set."
}

# ─── REPOSITORY VARIABLES ────────────────────────────────────────────────────
# Non-sensitive values. Safe to commit defaults here.
# Add a set_var call whenever a new vars.* reference appears in a workflow.

setup_vars() {
  echo "Setting repository variables..."

  # Application name — must match rootProject.name in settings.gradle.kts and the Helm chart name.
  # Used by: cd.yml (image-name), deploy.yml (APP_NAME, HELM_CHART path)
  set_var APP_NAME           "blitz-pay"

  # JDK version used by Gradle builds.
  # Used by: ci.yml (ORG_GRADLE_PROJECT_javaVersion), cd.yml (java-version)
  set_var JAVA_VERSION       "25"

  # Docker registry host + org prefix where images are pushed.
  # Used by: cd.yml (registry). Example: ghcr.io/medimohammadise
  set_var CONTAINER_REGISTRY "ghcr.io/medimohammadise"

  # OpenTelemetry logs endpoint (optional per-environment override).
  # Used by: deploy.yml (OTLP_LOGS_ENDPOINT). Example: http://loki.observability:3100/otlp
  set_var OTLP_LOGS_ENDPOINT ""

  echo ""
}

# ─── REPOSITORY SECRETS ──────────────────────────────────────────────────────
# Sensitive values — prompted interactively, never hardcoded.
# Add a set_secret call whenever a new secrets.* reference appears in a workflow.

setup_secrets() {
  echo "Setting repository secrets (you will be prompted for each value)..."
  echo ""

  # Qodana Cloud token for code quality reports.
  # Used by: ci.yml (via qodana workflow). Get from: https://qodana.cloud
  set_secret QODANA_TOKEN \
    "Qodana Cloud token — https://qodana.cloud"

  # Dependency-Track instance for SBOM upload (optional).
  # Used by: ci.yml (via dependency-track workflow).
  set_secret DTRACK_URL \
    "Dependency-Track base URL, e.g. https://dtrack.example.com" \
    optional

  set_secret DTRACK_API_KEY \
    "Dependency-Track API key" \
    optional

  echo ""
}

# ─── MAIN ────────────────────────────────────────────────────────────────────

echo "Configuring GitHub Actions environment for: $REPO"
echo ""

[ "$RUN_VARS"    = "true" ] && setup_vars
[ "$RUN_SECRETS" = "true" ] && setup_secrets

echo "Done. Verify at: https://github.com/$REPO/settings/variables/actions"
