#!/usr/bin/env bash
# fetch-k8s-ca.sh
#
# Fetches the CA certificate from a Kubernetes API server via TLS handshake
# and outputs it as a base64-encoded string (single line, no wrapping).
#
# Usage:
#   .github/scripts/fetch-k8s-ca.sh <server-url-or-host:port>
#
# Examples:
#   .github/scripts/fetch-k8s-ca.sh https://192.168.1.100:6443
#   .github/scripts/fetch-k8s-ca.sh 192.168.1.100:6443
#
# Prerequisites: openssl
#
# Exit codes:
#   0 — success (base64 cert printed to stdout)
#   1 — missing argument or unreachable server

set -euo pipefail

if [ $# -lt 1 ] || [ -z "$1" ]; then
  echo "Usage: $0 <server-url-or-host:port>" >&2
  exit 1
fi

# Strip protocol and trailing path to get host:port
host_port=$(echo "$1" | sed -E 's|https?://||; s|/.*||')

ca_pem=$(openssl s_client -connect "$host_port" -showcerts </dev/null 2>/dev/null \
  | awk '/-----BEGIN CERTIFICATE-----/{found=1} found{print} /-----END CERTIFICATE-----/{if(found) exit}')

if [ -z "$ca_pem" ]; then
  echo "ERROR: could not fetch CA certificate from $host_port" >&2
  echo "Make sure the server is reachable and the URL is correct." >&2
  exit 1
fi

echo "$ca_pem" | base64 | tr -d '\n'
