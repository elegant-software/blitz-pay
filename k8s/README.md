# Kubernetes Manifests

This directory contains Kubernetes manifests for deploying BlitzPay application.

## Files

- `deployment.yaml`: Complete Kubernetes deployment configuration including Deployment, Service, and ConfigMap

## Usage

### Manual Deployment

1. Create a secret with sensitive information:
```bash
kubectl create secret generic blitzpay-secrets \
  --from-literal=DATABASE_URL="jdbc:postgresql://postgres:5432/quickpay_db" \
  --from-literal=TRUELAYER_CLIENT_ID="your-client-id" \
  --from-literal=TRUELAYER_CLIENT_SECRET="your-client-secret" \
  --from-literal=TRUELAYER_KEY_ID="your-key-id" \
  --from-literal=TRUELAYER_MERCHANT_ACCOUNT_ID="your-merchant-account-id" \
  --namespace=default
```

2. Apply the manifests:
```bash
kubectl apply -f k8s/deployment.yaml --namespace=default
```

3. Check deployment status:
```bash
kubectl get pods -l app=blitzpay
kubectl get svc blitzpay
```

### Using with GitHub Actions

These manifests are used as fallback by the GitHub Actions deployment workflow when Arconia plugin is not available. The workflow automatically:
- Creates/updates ConfigMaps
- Creates/updates Secrets
- Applies the deployment
- Monitors rollout status

## Configuration

### Environment Variables

The application can be configured using environment variables:

**Database:**
- `DATABASE_URL`: JDBC connection URL for PostgreSQL

**TrueLayer:**
- `TRUELAYER_CLIENT_ID`: TrueLayer API client ID
- `TRUELAYER_CLIENT_SECRET`: TrueLayer API client secret
- `TRUELAYER_KEY_ID`: TrueLayer signing key ID
- `TRUELAYER_MERCHANT_ACCOUNT_ID`: TrueLayer merchant account ID

**Spring:**
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (dev, staging, prod)

### Resource Limits

Default resource configuration:
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 1000m CPU

Adjust these values based on your workload requirements in `deployment.yaml`.

### Health Checks

The deployment includes:
- **Liveness Probe**: Checks if the application is running
  - Path: `/actuator/health/liveness`
  - Initial delay: 60 seconds
  - Period: 10 seconds

- **Readiness Probe**: Checks if the application is ready to serve traffic
  - Path: `/actuator/health/readiness`
  - Initial delay: 30 seconds
  - Period: 5 seconds

## Scaling

To scale the deployment:
```bash
kubectl scale deployment blitzpay --replicas=3
```

Or edit the `replicas` field in `deployment.yaml` and reapply.

## Accessing the Application

### Within the cluster
```bash
curl http://blitzpay.default.svc.cluster.local/actuator/health
```

### Port forwarding for local access
```bash
kubectl port-forward service/blitzpay 8080:80
curl http://localhost:8080/actuator/health
```

### Using Ingress (if configured)
Set up an Ingress resource to expose the application externally.

## Troubleshooting

### Check pod logs
```bash
kubectl logs -f deployment/blitzpay
```

### Describe pod for events
```bash
kubectl describe pod -l app=blitzpay
```

### Check deployment status
```bash
kubectl rollout status deployment/blitzpay
```

### Restart deployment
```bash
kubectl rollout restart deployment/blitzpay
```

## Arconia Integration

For automated Kubernetes manifest generation using Arconia, the GitHub Actions workflow will:
1. Add the Arconia plugin to `build.gradle.kts`
2. Generate manifests with `./gradlew bootBuildImage`
3. Apply generated manifests from `build/arconia/`

If Arconia generation fails, the workflow falls back to the manifests in this directory.
