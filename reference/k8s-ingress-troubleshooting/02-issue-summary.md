# Issue Summary — Ingress Access Fix

## Goal
Access the app at `https://api.blitz-pay.local/swagger-ui/index.html` from a Mac,
where the app runs in a kind (local Kubernetes) cluster on a remote server (`192.168.2.103`).

---

## Root Causes Found (in order of discovery)

### 1. Wrong hostname
The Helm chart was deployed with `values-staging.yaml`, so the Ingress was configured for
`api.staging.blitz-pay.com` — not `api.blitz-pay.local`.

**Fix:** Used the staging hostname consistently and added it to `/etc/hosts` on the Mac:
```
192.168.2.103   api.staging.blitz-pay.com
```

---

### 2. No path from Mac → kind cluster (main problem)
kind runs Kubernetes inside Docker containers on the server. Those containers live on an
internal Docker network (`172.19.0.x`) that is **not directly reachable** from the Mac.

```
Mac (192.168.2.102)  →  Server (192.168.2.103)  →  kind Docker network (172.19.0.x)
                                                          ↑
                                               Nothing bridging this gap
```

The ingress-nginx Service was type `NodePort` — meaning it only listened on the kind nodes'
internal IPs, not on the server's real IP.

**Things tried:**
- `externalIPs` patch on the nginx service → did not work because the server's IP
  is not assigned to any kind node interface
- `kubectl port-forward` → failed due to `kubectl` version skew (client v1.35 vs server v1.30)
- Downgraded kubectl to v1.30 → port-forward still failed (API group discovery errors)

**Final fix:** Installed `socat` on the server to manually bridge the gap:
```bash
sudo socat TCP-LISTEN:80,fork TCP:172.19.0.9:30423 &
sudo socat TCP-LISTEN:443,fork TCP:172.19.0.9:30320 &
```
This opens ports 80/443 on the server and tunnels traffic into the ingress controller.

---

### 3. Missing TLS certificate secret
The Ingress had TLS configured, pointing to a secret `blitz-pay-tls-staging` that did not
exist. When this secret is missing, nginx-ingress cannot set up the virtual host and
returns **404 for all requests** to that hostname.

**Fix:** Removed TLS from the Ingress (fine for local/staging access):
```bash
kubectl patch ingress blitz-pay -n blitzpay-staging \
  --type=json \
  -p='[
    {"op":"remove","path":"/spec/tls"},
    {"op":"remove","path":"/metadata/annotations/nginx.ingress.kubernetes.io~1server-snippet"}
  ]'
```
Note: the `server-snippet` annotation also had to be removed because the nginx-ingress
admission webhook had snippets disabled and rejected any patch while it was present.

---

## Final Working State

| Component | Status |
|---|---|
| ingress-nginx controller | Running in `ingress-nginx` namespace |
| socat (port 80 → kind node 30423) | Running on server |
| socat (port 443 → kind node 30320) | Running on server |
| `/etc/hosts` on Mac | `192.168.2.103 api.staging.blitz-pay.com` |
| Ingress TLS | Removed (HTTP only for local access) |
| App pod | Running in `blitzpay-staging` namespace |

**Working URL:**
```
http://api.staging.blitz-pay.com/swagger-ui/index.html
```

---

## Known Limitations of Current Setup

1. **socat is not persistent** — if the server restarts, socat processes are lost.
   Run them again or set up a systemd service.
2. **HTTP only** — TLS was removed. For HTTPS, create a self-signed cert secret or
   use cert-manager with a self-signed issuer.
3. **socat is a workaround** — the proper fix is to configure kind with `extraPortMappings`
   at cluster creation time so ports 80/443 on the server map directly into the ingress
   controller. See `03-terraform-kind-ingress.md`.
