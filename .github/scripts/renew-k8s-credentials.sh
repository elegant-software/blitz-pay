#!/usr/bin/env bash
# renew-k8s-credentials.sh
#
# Fixes kubectl TLS errors (x509 SAN mismatch / unknown CA) by updating the
# current kubeconfig context with the correct API server URL and its CA cert.
#
# Usage:
#   .github/scripts/renew-k8s-credentials.sh <ip-or-url> [port]
#
# Examples:
#   .github/scripts/renew-k8s-credentials.sh 172.19.0.6
#   .github/scripts/renew-k8s-credentials.sh 172.19.0.6 6443
#   .github/scripts/renew-k8s-credentials.sh https://172.19.0.6:6443
#
# What it does:
#   1. Fetches the CA certificate from the server via fetch-k8s-ca.sh
#   2. Updates the current kubeconfig cluster entry with:
#      - the provided server URL
#      - the fetched CA cert (base64, embedded in kubeconfig)
#
# Prerequisites: kubectl, openssl

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ] || [ -z "$1" ]; then
  echo "Usage: $0 <ip-or-url> [port]"
  echo "Example: $0 172.19.0.6"
  exit 1
fi

input="$1"
port="${2:-6443}"

# Normalise: accept bare IP, IP:port, or full URL
if [[ "$input" =~ ^https?:// ]]; then
  server_url="$input"
else
  # Strip any trailing port so we can apply the explicit $port
  bare_host="${input%%:*}"
  server_url="https://${bare_host}:${port}"
fi

# ─── Resolve current context and cluster ─────────────────────────────────────

context=$(kubectl config current-context)
cluster=$(kubectl config view -o jsonpath="{.contexts[?(@.name=='$context')].context.cluster}")

if [ -z "$cluster" ]; then
  echo "ERROR: could not determine cluster for context '$context'" >&2
  exit 1
fi

echo "Context : $context"
echo "Cluster : $cluster"
echo "Server  : $server_url"
echo ""

# ─── Fetch CA certificate ────────────────────────────────────────────────────

echo "Fetching CA certificate from $server_url ..."
ca_b64=$("$SCRIPT_DIR/fetch-k8s-ca.sh" "$server_url")
echo "CA certificate fetched."
echo ""

# ─── Update kubeconfig ───────────────────────────────────────────────────────

echo "Updating kubeconfig cluster '$cluster' ..."

kubectl config set-cluster "$cluster" \
  --server="$server_url" \
  --certificate-authority=<(echo "$ca_b64" | base64 -d) \
  --embed-certs=true

echo ""
echo "Done. Verify with:"
echo "  kubectl cluster-info"
echo "  kubectl get nodes"
