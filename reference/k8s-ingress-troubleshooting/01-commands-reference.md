# Commands Reference — Ingress Troubleshooting Session
> Beginner-friendly explanation of every command used

---

## Checking What's Running

```bash
kubectl get pods -n ingress-nginx -w
```
> Lists all pods (containers) inside the `ingress-nginx` area of the cluster. The `-w` flag "watches" for live updates. Like checking if your traffic cop is on duty.

```bash
kubectl get pods -A
```
> Lists ALL pods across every area (namespace) in the cluster. Good for a full health check.

```bash
kubectl get ingress -A
```
> Lists all Ingress rules across the cluster. An Ingress rule is like a receptionist that directs incoming web traffic to the right app.

```bash
kubectl get svc -n ingress-nginx
```
> Lists the "Services" (network doors) inside the ingress-nginx area. Shows what ports are open and what type they are (NodePort, LoadBalancer, etc).

```bash
kubectl get nodes -o wide
```
> Lists all machines (nodes) in the cluster with their IP addresses. `-o wide` shows extra details like internal IPs.

```bash
kubectl get secret -n blitzpay-staging
```
> Lists all secrets (passwords, certificates, keys) stored in the `blitzpay-staging` area.

---

## Inspecting Resources in Detail

```bash
kubectl describe ingress blitz-pay -n blitzpay-staging
```
> Shows the full details of the Ingress rule named `blitz-pay`. Includes which hostnames it handles, which paths it routes, and any events/errors.

```bash
kubectl get svc ingress-nginx-controller -n ingress-nginx -o yaml
```
> Shows the full configuration of the ingress controller's Service in YAML format. `-o yaml` means "give me the raw config file".

```bash
kubectl get endpoints blitz-pay -n blitzpay-staging
```
> Shows which pod IP addresses are behind the `blitz-pay` service. Confirms the app is actually registered and reachable.

```bash
kubectl get secret blitz-pay-tls-staging -n blitzpay-staging
```
> Checks if the TLS (HTTPS) certificate secret exists. If it doesn't exist, HTTPS won't work.

---

## Checking App Health

```bash
kubectl logs -n blitzpay-staging deploy/blitz-pay --tail=20
```
> Shows the last 20 log lines from the running app. Like reading the app's diary to see what it's been doing and if there are errors.

---

## Making Changes

```bash
kubectl patch svc ingress-nginx-controller -n ingress-nginx \
  -p '{"spec":{"externalIPs":["192.168.2.103"]}}'
```
> Modifies the ingress controller's Service to also respond on the server's IP address (`192.168.2.103`). Like adding an extra front door to the building.

```bash
kubectl patch ingress blitz-pay -n blitzpay-staging \
  --type=json \
  -p='[
    {"op":"remove","path":"/spec/tls"},
    {"op":"remove","path":"/metadata/annotations/nginx.ingress.kubernetes.io~1server-snippet"}
  ]'
```
> Removes TLS (HTTPS) and a custom nginx rule from the Ingress. The `--type=json` means we're giving exact instructions on what to add/remove/replace, like surgery on the config.

---

## Fixing kubectl Version

```bash
curl -LO "https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/kubectl
```
> Downloads kubectl version 1.30 (to match the cluster version), makes it executable, and installs it. Like making sure your TV remote matches your TV model.

```bash
kubectl version
```
> Shows the version of both the kubectl tool (client) and the Kubernetes cluster (server). They should be within 1 version of each other.

---

## Network Forwarding (socat)

```bash
sudo apt-get install -y socat
```
> Installs `socat`, a tool that creates network tunnels. Like installing a pipe between two rooms.

```bash
sudo socat TCP-LISTEN:80,fork TCP:172.19.0.9:30423 &
```
> Opens port 80 on the server and forwards all traffic to the ingress controller inside the kind cluster (at `172.19.0.9:30423`). The `&` runs it in the background.

```bash
sudo socat TCP-LISTEN:443,fork TCP:172.19.0.9:30320 &
```
> Same as above but for HTTPS (port 443 → 30320).

---

## Testing Connectivity

```bash
curl -sv http://192.168.2.103:30423/swagger-ui/index.html
```
> Tries to reach the app via the server's IP and NodePort. `-sv` shows verbose output so you can see exactly what's happening with the connection.

```bash
curl -v --resolve api.staging.blitz-pay.com:80:192.168.2.103 \
  http://api.staging.blitz-pay.com/swagger-ui/index.html
```
> Tests the URL but manually tells curl to resolve the hostname to a specific IP. Like testing if a door works before putting up the sign.
