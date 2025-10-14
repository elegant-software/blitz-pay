# GitHub Actions Workflows

This directory contains reusable GitHub Actions workflows for the BlitzPay application.

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

### 3. Deploy Workflow (`deploy.yml`)

A reusable workflow for deploying the application to Kubernetes using Arconia library.

**Features:**
- Deploys to Kubernetes using kubectl
- Supports Arconia plugin for manifest generation
- Fallback to manual Kubernetes manifests if Arconia not available
- Manages ConfigMaps and Secrets
- Includes health checks and readiness probes
- Supports multiple environments (dev, staging, prod)

**Usage:**
```yaml
jobs:
  deploy:
    uses: ./.github/workflows/deploy.yml
    with:
      environment: 'dev'
      image-uri: 'ghcr.io/owner/blitzpay:sha-abc123'
      namespace: 'blitzpay-dev'
      replicas: 1
    secrets:
      kubeconfig: ${{ secrets.KUBECONFIG_DEV }}
      database-url: ${{ secrets.DATABASE_URL_DEV }}
      truelayer-client-id: ${{ secrets.TRUELAYER_CLIENT_ID_DEV }}
      truelayer-client-secret: ${{ secrets.TRUELAYER_CLIENT_SECRET_DEV }}
```

**Inputs:**
- `environment` (required): Deployment environment (dev, staging, prod)
- `image-uri` (required): Full image URI to deploy
- `namespace` (optional, default: 'default'): Kubernetes namespace
- `app-name` (optional, default: 'blitzpay'): Application name (Note: When using k8s/deployment.yaml fallback, the app name remains 'blitzpay'. For custom names, use Arconia or inline manifests)
- `replicas` (optional, default: 1): Number of replicas
- `arconia-version` (optional, default: '0.6.0'): Arconia Gradle plugin version

**Secrets:**
- `kubeconfig` (required): Base64-encoded Kubernetes config
- `database-url` (optional): Database connection URL
- `truelayer-client-id` (optional): TrueLayer client ID
- `truelayer-client-secret` (optional): TrueLayer client secret

### 4. CI/CD Pipeline (`ci-cd.yml`)

Main CI/CD pipeline that orchestrates all workflows.

**Features:**
- Automatic testing on push and pull requests
- Automatic deployment to dev on develop branch
- Automatic deployment to staging on main/master branch
- Manual deployment to production via workflow_dispatch
- Environment-specific configuration

**Triggers:**
- Push to main, master, or develop branches
- Pull requests to main, master, or develop branches
- Manual workflow dispatch with environment selection

## Setup Instructions

### 1. Repository Secrets

Configure the following secrets in your GitHub repository:

**Development:**
- `KUBECONFIG_DEV`: Base64-encoded kubeconfig for dev cluster
- `DATABASE_URL_DEV`: Development database URL
- `TRUELAYER_CLIENT_ID_DEV`: TrueLayer client ID for dev
- `TRUELAYER_CLIENT_SECRET_DEV`: TrueLayer client secret for dev

**Staging:**
- `KUBECONFIG_STAGING`: Base64-encoded kubeconfig for staging cluster
- `DATABASE_URL_STAGING`: Staging database URL
- `TRUELAYER_CLIENT_ID_STAGING`: TrueLayer client ID for staging
- `TRUELAYER_CLIENT_SECRET_STAGING`: TrueLayer client secret for staging

**Production:**
- `KUBECONFIG_PROD`: Base64-encoded kubeconfig for production cluster
- `DATABASE_URL_PROD`: Production database URL
- `TRUELAYER_CLIENT_ID_PROD`: TrueLayer client ID for production
- `TRUELAYER_CLIENT_SECRET_PROD`: TrueLayer client secret for production

### 2. Prepare Kubeconfig

To encode your kubeconfig:
```bash
cat ~/.kube/config | base64 -w 0
```

### 3. Arconia Integration

The deploy workflow includes Arconia Gradle plugin integration for generating Kubernetes manifests. If Arconia is not configured, the workflow falls back to generating basic Kubernetes manifests.

To use Arconia, ensure your `build.gradle.kts` includes:
```kotlin
plugins {
    id("io.github.polargradient.arconia") version "0.6.0"
}
```

## Deployment Flow

1. **Development:**
   - Push to `develop` branch
   - Tests run automatically
   - Docker image is built
   - Application deploys to dev environment

2. **Staging:**
   - Push to `main` or `master` branch
   - Tests run automatically
   - Docker image is built
   - Application deploys to staging environment

3. **Production:**
   - Use workflow_dispatch to manually trigger deployment
   - Select 'prod' environment
   - Application deploys to production after staging validation

## Monitoring Deployments

After deployment, check the status:
```bash
kubectl get pods -n <namespace>
kubectl get deployment <app-name> -n <namespace>
kubectl logs -f deployment/<app-name> -n <namespace>
```

## Rollback

To rollback a deployment:
```bash
kubectl rollout undo deployment/<app-name> -n <namespace>
```

## Troubleshooting

### Build Failures
- Check test results artifacts in the Actions tab
- Review build logs for Gradle errors
- Ensure all dependencies are available

### Deployment Failures
- Verify kubeconfig secret is correctly configured
- Check pod logs: `kubectl logs -f <pod-name> -n <namespace>`
- Check deployment events: `kubectl describe deployment <app-name> -n <namespace>`
- Verify image is accessible from the cluster

### Image Pull Errors
- Ensure GitHub Container Registry permissions are set correctly
- Verify the image was pushed successfully
- Check if image pull secrets are configured in the cluster

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Arconia GitHub Repository](https://github.com/polargradient/arconia)
- [Docker Documentation](https://docs.docker.com/)
