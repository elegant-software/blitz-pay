# GitHub Actions CI/CD Setup

This document provides a quick reference for the GitHub Actions workflows configured for this project.

## Overview

The project uses reusable GitHub Actions workflows to automate testing, building, and deploying the BlitzPay application to Kubernetes.

## Workflows

1. **Test** (`.github/workflows/test.yml`) - Runs tests with PostgreSQL
2. **Build** (`.github/workflows/build.yml`) - Builds Docker image and pushes to GitHub Container Registry
3. **Deploy** (`.github/workflows/deploy.yml`) - Deploys to Kubernetes using Arconia library
4. **CI/CD** (`.github/workflows/ci-cd.yml`) - Main pipeline orchestrating all workflows and optionally uploading a CycloneDX SBOM to Dependency-Track when the necessary secrets are configured

## Quick Start

### 1. Configure Repository Secrets

Go to `Settings > Secrets and variables > Actions` and add:

#### Development Environment
- `KUBECONFIG_DEV` - Base64-encoded kubeconfig
- `DATABASE_URL_DEV` - Database connection string
- `TRUELAYER_CLIENT_ID_DEV` - TrueLayer client ID
- `TRUELAYER_CLIENT_SECRET_DEV` - TrueLayer client secret

#### Staging Environment
- `KUBECONFIG_STAGING`
- `DATABASE_URL_STAGING`
- `TRUELAYER_CLIENT_ID_STAGING`
- `TRUELAYER_CLIENT_SECRET_STAGING`

#### Production Environment
- `KUBECONFIG_PROD`
- `DATABASE_URL_PROD`
- `TRUELAYER_CLIENT_ID_PROD`
- `TRUELAYER_CLIENT_SECRET_PROD`

#### Security Scanning (Optional)
- `DTRACK_URL` - Base URL for your Dependency-Track instance (e.g., `https://dependency-track.example.com`)
- `DTRACK_API_KEY` - API key with permission to upload BOMs via `/api/v1/bom`
- `DTRACK_PROJECT_NAME` (optional) - Overrides the `projectName` field sent to Dependency-Track (defaults to `${{ github.repository }}`)
- `DTRACK_PROJECT_VERSION` (optional) - Overrides the `projectVersion` field (defaults to `${{ github.sha }}`)

### Dependency-Track SBOM Upload
The CI/CD pipeline runs a job that executes `./gradlew cyclonedxBom` and uploads the resulting `build/reports/cyclonedx/bom.xml` to Dependency-Track when `DTRACK_URL` and `DTRACK_API_KEY` are set in your repository secrets. The upload POSTs to `<DTRACK_URL>/api/v1/bom` with `autoCreate=true`, `projectName`/`projectVersion` defaults as noted above, and the SBOM attached via multipart/form-data.

### 2. Prepare Kubeconfig Secret

```bash
# Encode your kubeconfig
cat ~/.kube/config | base64 -w 0

# Add the output as a secret in GitHub
```

### 3. Trigger Workflows

**Automatic:**
- Push to `develop` → Tests + Build + Deploy to Dev
- Push to `main`/`master` → Tests + Build + Deploy to Staging
- Pull requests → Tests only

**Manual:**
- Go to Actions tab
- Select "CI/CD Pipeline"
- Click "Run workflow"
- Choose environment (dev/staging/prod)

## Deployment Environments

| Environment | Branch Trigger | Replicas | Namespace |
|------------|----------------|----------|-----------|
| Development | `develop` | 1 | `blitzpay-dev` |
| Staging | `main`/`master` | 2 | `blitzpay-staging` |
| Production | Manual only | 3 | `blitzpay-prod` |

## Arconia Integration

The deployment workflow supports the Arconia Gradle plugin for generating Kubernetes manifests:

```kotlin
plugins {
    id("io.github.polargradient.arconia") version "0.6.0"
}
```

If Arconia is not available, the workflow automatically falls back to standard Kubernetes manifests located in the `k8s/` directory.

## Manual Kubernetes Deployment

If you prefer to deploy manually:

```bash
# Create secrets
kubectl create secret generic blitzpay-secrets \
  --from-literal=DATABASE_URL="your-db-url" \
  --from-literal=TRUELAYER_CLIENT_ID="your-client-id" \
  --from-literal=TRUELAYER_CLIENT_SECRET="your-secret" \
  --namespace=default

# Apply manifests
kubectl apply -f k8s/deployment.yml --namespace=default

# Check status
kubectl get pods -l app=blitzpay
kubectl logs -f deployment/blitzpay
```

## Monitoring

### Check Workflow Status
- Go to the Actions tab in GitHub
- View workflow runs and logs

### Check Kubernetes Deployment
```bash
# Get deployment status
kubectl get deployment blitzpay -n <namespace>

# Get pods
kubectl get pods -n <namespace> -l app=blitzpay

# View logs
kubectl logs -f deployment/blitzpay -n <namespace>

# Check events
kubectl get events -n <namespace> --sort-by='.lastTimestamp'
```

## Troubleshooting

### Build Failures
1. Check test results in Actions artifacts
2. Review Gradle build logs
3. Ensure dependencies are available

### Deployment Failures
1. Verify kubeconfig secret is correct
2. Check pod logs: `kubectl logs <pod-name>`
3. Describe deployment: `kubectl describe deployment blitzpay`
4. Verify image can be pulled from the cluster

### Rollback
```bash
kubectl rollout undo deployment/blitzpay -n <namespace>
```

## Documentation

- Detailed workflow documentation: `.github/workflows/README.md`
- Kubernetes manifests documentation: `k8s/README.md`

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review pod logs in Kubernetes
3. Consult the detailed documentation in `.github/workflows/README.md`
