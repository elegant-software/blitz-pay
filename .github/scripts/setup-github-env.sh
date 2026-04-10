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
#   - kubectl configured with access to the target cluster
#   - openssl installed (for CA cert extraction)
#   - Run from the root of the repository
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

# ─── KUBERNETES SETUP ────────────────────────────────────────────────────────
# Sets K8S_SERVER + K8S_CA_CERT secrets and creates the deploy service account.
# No token is stored — deploy.yml generates a short-lived token each run.

setup_k8s() {
  echo "Setting up Kubernetes access..."
  echo ""

  # --- K8S_SERVER ---
  read -r -p "  [secret] K8S_SERVER (API server URL, e.g. https://192.168.1.100:6443): " k8s_server
  while [ -z "$k8s_server" ]; do
    echo "           This is required."
    read -r -p "  [secret] K8S_SERVER: " k8s_server
  done

  gh secret set K8S_SERVER --body "$k8s_server" --repo "$REPO"
  echo "           Set."

  # --- K8S_CA_CERT (auto-fetched from the server) ---
  local host_port
  host_port=$(echo "$k8s_server" | sed -E 's|https?://||; s|/.*||')

  echo "  [secret] K8S_CA_CERT — fetching CA certificate from $host_port ..."
  local ca_b64
  ca_b64=$("$(dirname "$0")/fetch-k8s-ca.sh" "$k8s_server")

  gh secret set K8S_CA_CERT --body "$ca_b64" --repo "$REPO"
  echo "           Set (fetched from $host_port)."

  # --- Service account + long-lived token ---
  local sa_name="blitzpay-deploy"
  local sa_ns="kube-system"
  local secret_name="${sa_name}-token"

  echo ""
  echo "  Creating service account $sa_ns/$sa_name ..."

  kubectl get sa "$sa_name" -n "$sa_ns" &>/dev/null || \
    kubectl create sa "$sa_name" -n "$sa_ns"

  kubectl get clusterrolebinding "$sa_name-binding" &>/dev/null || \
    kubectl create clusterrolebinding "$sa_name-binding" \
      --clusterrole=cluster-admin \
      --serviceaccount="$sa_ns:$sa_name"

  # Create a Secret-based long-lived token (works on all K8S versions)
  if ! kubectl get secret "$secret_name" -n "$sa_ns" &>/dev/null; then
    kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: $secret_name
  namespace: $sa_ns
  annotations:
    kubernetes.io/service-account.name: $sa_name
type: kubernetes.io/service-account-token
EOF
    echo "  Created secret $sa_ns/$secret_name"
    # Wait for the token controller to populate the token
    sleep 2
  fi

  local token
  token=$(kubectl get secret "$secret_name" -n "$sa_ns" -o jsonpath='{.data.token}' | base64 -d)

  if [ -z "$token" ]; then
    echo "  ERROR: token not populated in secret $secret_name"
    exit 1
  fi

  gh secret set K8S_TOKEN --body "$token" --repo "$REPO"
  echo "  [secret] K8S_TOKEN — extracted from secret $sa_ns/$secret_name"
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

  # Kubernetes cluster connection
  setup_k8s

  echo ""
}

# ─── MAIN ────────────────────────────────────────────────────────────────────

echo "Configuring GitHub Actions environment for: $REPO"
echo ""

[ "$RUN_VARS"    = "true" ] && setup_vars
[ "$RUN_SECRETS" = "true" ] && setup_secrets

echo "Done. Verify at: https://github.com/$REPO/settings/variables/actions"
