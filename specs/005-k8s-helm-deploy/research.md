# Research: Kubernetes Helm Deployment for BlitzPay

**Branch**: `005-k8s-helm-deploy` | **Date**: 2026-03-27

## 1. Helm Chart Structure

**Decision**: Single Helm chart at `helm/blitzpay/` with environment-specific values files.

**Rationale**: A single chart for a single microservice follows CNCF Helm best practices. Environment-specific overrides (`values-staging.yaml`, `values-prod.yaml`) layered on top of a base `values.yaml` keep the chart DRY and maintainable. Essential templates: `deployment.yaml`, `service.yaml`, `ingress.yaml`, `configmap.yaml`, `_helpers.tpl`, `NOTES.txt`.

**Alternatives Considered**:
- Kustomize + plain YAML: Valid, but Helm provides simpler one-command deploys and cleaner lifecycle management for a team just starting with K8s.
- Separate charts per environment: Violates DRY; template duplication makes maintenance difficult.
- Helmfile: Useful for multi-service orchestration; overkill for a single microservice.

---

## 2. Secret Management: Referencing K8s Secrets Without Storing Values in Git

**Decision**: Pre-create `blitzpay-secrets` Kubernetes Secret in each cluster namespace (manually or via secure channel). Helm chart templates reference the secret by name via `secretRef` / `secretKeyRef` — secret values never appear in version-controlled files.

**Pattern**:
```yaml
# deployment.yml template snippet
envFrom:
  - configMapRef:
      name: {{ include "blitzpay.fullname" . }}-config
  - secretRef:
      name: {{ .Values.secrets.name }}
```

```yaml
# values.yaml
secrets:
  name: blitzpay-secrets
```

**Secret contents** (created by platform engineer per environment):
| Secret Key | Source | Sensitivity |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection string | High |
| `SPRING_DATASOURCE_USERNAME` | DB username | High |
| `SPRING_DATASOURCE_PASSWORD` | DB password | High |
| `TRUELAYER_CLIENT_ID` | TrueLayer API credential | High |
| `TRUELAYER_CLIENT_SECRET` | TrueLayer API credential | High |
| `TRUELAYER_KEY_ID` | TrueLayer signing key ID | High |
| `TRUELAYER_PRIVATE_KEY_PATH` | Path to EC private key file | High |
| `TRUELAYER_MERCHANT_ACCOUNT_ID` | TrueLayer merchant ID | Medium |
| `TRUELAYER_ACCESS_TOKEN` | TrueLayer access token | High |

**Non-sensitive config** (ConfigMap):
- `SPRING_PROFILES_ACTIVE`
- `TRUELAYER_API_BASE`
- `TRUELAYER_ENV`
- `TRUELAYER_HTTP_LOGS`
- `TRUELAYER_WEBHOOK_JKU`

**Alternatives Considered**:
- SOPS + helm-secrets: Good for audit trails and secret versioning in Git; adds tooling complexity not needed at this stage.
- Sealed Secrets: Lightweight in-cluster encryption; valid middle ground if secret rotation tracking is needed.
- Embedding values in `values.yaml`: Anti-pattern; never acceptable.

---

## 3. AWS Secrets Manager Migration Path

**Decision**: Design for External Secrets Operator (ESO) as the future migration mechanism. Current implementation uses native K8s Secrets. ESO syncs AWS Secrets Manager entries to native K8s Secrets automatically, requiring zero changes to Helm templates or application code.

**Migration steps (when ready)**:
1. Install ESO Helm chart in cluster
2. Create AWS IAM role for ESO pods (IRSA on EKS)
3. Create `SecretStore` resource pointing to AWS Secrets Manager
4. Create `ExternalSecret` resources mapping AWS secrets → K8s Secrets
5. ESO auto-syncs; Helm chart templates are unchanged

**Rationale**: ESO is AWS-recommended for EKS. It outputs native K8s Secrets, so `secretRef` / `secretKeyRef` references in Helm templates remain valid. Fargate-compatible. No application code changes needed.

**Alternatives Considered**:
- Secrets Store CSI Driver: Mounts secrets as files; may require app code changes; no Fargate support; use only when security team prohibits secrets in etcd.
- HashiCorp Vault: Enterprise-grade; operational overhead not justified for a single microservice.

---

## 4. Ingress Configuration for Spring Boot + Swagger UI

**Decision**: Nginx Ingress with `pathType: Prefix` for all routes. Three path prefixes: `/api-docs` (OpenAPI), `/swagger-ui` (Swagger UI), and catch-all for REST endpoints. TLS terminated at ingress via cert-manager.

**Swagger UI path discovery**: From `application.yml`, the app uses:
- `springdoc.api-docs.path: /api-docs` (custom, not the default `/v3/api-docs`)
- Swagger UI at default `/swagger-ui` path

**Ingress path mapping**:
| External Path | Target | Notes |
|---|---|---|
| `/api-docs` | port 8080 | OpenAPI JSON schema |
| `/swagger-ui` | port 8080 | Swagger UI browser interface |
| `/` (catch-all) | port 8080 | All REST API endpoints |

**Key annotations** (Nginx):
```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
cert-manager.io/cluster-issuer: letsencrypt-prod
```

**Note on `rewrite-target`**: Since all paths route to the same service on the same paths (no path prefix stripping needed), `rewrite-target` is not required. The application already handles `/swagger-ui` and `/api-docs` internally.

**Alternatives Considered**:
- Separate subdomains for Swagger (e.g., `docs.example.com`): Adds DNS/TLS complexity; single-host approach simpler.
- API Gateway (Kong, Ambassador): Overkill for a single microservice.
- `pathType: Exact`: Too restrictive for REST API hierarchies.

---

## 5. CD Pipeline Helm Deploy Step

**Decision**: Add a `deploy` job to the existing `cd.yml` workflow. After the `release` job builds and pushes the image, the `deploy` job runs `helm upgrade --install` with the image tag from `github.ref_name`.

**Pattern**:
```yaml
deploy:
  needs: release
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: azure/setup-helm@v3
    - name: Configure kubectl
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBECONFIG }}" | base64 -d > $HOME/.kube/config
    - name: Deploy
      run: |
        helm upgrade --install blitzpay ./helm/blitzpay \
          --namespace blitzpay-prod \
          --create-namespace \
          --values ./helm/blitzpay/values.yaml \
          --values ./helm/blitzpay/values-prod.yaml \
          --set image.tag=${{ github.ref_name }} \
          --wait \
          --timeout 5m
```

**GitHub Actions secrets required**:
- `KUBECONFIG`: base64-encoded kubeconfig for the target cluster

**Alternatives Considered**:
- `helm install` only: Fails if release already exists; not idempotent.
- ArgoCD / FluxCD: Better for multi-team GitOps; overkill at this stage.
- `kubectl apply -f`: Loses Helm lifecycle management and rollback.

---

## 6. Multi-Environment Values Strategy

**Decision**: Base `values.yaml` with sensible defaults, overridden by `values-staging.yaml` and `values-prod.yaml`. Helm merges files in order (last wins). Environment files override only what differs.

**Key per-environment differences**:
| Setting | Staging | Production |
|---|---|---|
| `replicaCount` | 1 | 2 |
| `ingress.hostname` | `api.staging.blitzpay.com` (placeholder) | `api.blitzpay.com` (placeholder) |
| `ingress.tlsSecretName` | `blitzpay-tls-staging` | `blitzpay-tls-prod` |
| `resources.requests.cpu` | 250m | 500m |
| `resources.requests.memory` | 512Mi | 768Mi |

**Secrets name** is the same key (`secrets.name: blitzpay-secrets`) in both environments; the actual K8s Secret is created separately in each namespace.

---

## Summary

| Decision | Choice | Key Reason |
|---|---|---|
| Chart structure | Single chart, layered values files | DRY, standard Helm pattern |
| Secrets | Pre-created K8s Secret, `secretRef` in templates | Zero secrets in Git |
| AWS migration | External Secrets Operator | No app/chart changes on migration |
| Ingress routing | Nginx, `pathType: Prefix`, 3 path rules | Matches app's internal paths |
| CD deploy | `helm upgrade --install` with `--set image.tag` | Idempotent, tag override from build |
| Multi-env | `values.yaml` + `values-{staging,prod}.yaml` | Minimal duplication |
