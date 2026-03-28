# Agent Support Document — Terraform kind + ingress-nginx (Proper Setup)

## Context
This document is for a Claude agent working on the infrastructure Terraform project that
manages a kind (Kubernetes-in-Docker) cluster. The goal is to replace the current
`socat` port-forwarding workaround with a proper setup where ingress-nginx natively
exposes ports 80 and 443 on the host machine.

## Current Problem Being Solved
The kind cluster was created without `extraPortMappings`, so ports 80/443 on the host
server are not connected to the ingress controller inside the cluster. Currently `socat`
bridges this gap as a workaround. This Terraform work makes socat unnecessary.

---

## Required Changes

### 1. kind Cluster — Add `extraPortMappings` to control-plane node

The kind cluster config must map host ports 80 and 443 to the ingress-nginx controller
container ports. This is done at cluster creation time.

**Using the Terraform `kind` provider (`tehcyx/kind`):**

```hcl
resource "kind_cluster" "default" {
  name           = "blitz-cluster"
  wait_for_ready = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    # Control-plane node gets the port mappings
    node {
      role = "control-plane"

      # This is the key addition — maps host ports to container ports
      extra_port_mappings {
        container_port = 80
        host_port      = 80
        protocol       = "TCP"
      }

      extra_port_mappings {
        container_port = 443
        host_port      = 443
        protocol       = "TCP"
      }

      # Label required so ingress-nginx knows which node to run on
      kubeadm_config_patches = [
        "kind: InitConfiguration\nnodeRegistration:\n  kubeletExtraArgs:\n    node-labels: \"ingress-ready=true\"\n"
      ]
    }

    # Worker nodes (no port mappings needed)
    node {
      role = "worker"
    }

    node {
      role = "worker"
    }

    node {
      role = "worker"
    }

    node {
      role = "worker"
    }
  }
}
```

> **Important:** `extraPortMappings` can only be set at cluster creation. The existing
> cluster must be destroyed and recreated (`terraform destroy` then `terraform apply`).
> All workloads (Helm releases, secrets, etc.) must be redeployed after recreation.

---

### 2. ingress-nginx — Deploy with kind-specific configuration

The standard ingress-nginx Helm chart must be configured with `hostPort` enabled so the
ingress controller pod binds directly to the node's ports 80/443 (which are mapped to the
host via `extraPortMappings`).

**Using the `helm_release` Terraform resource:**

```hcl
resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  version          = "4.12.1"   # pin to a stable version

  # Wait for the controller to be ready before proceeding
  wait    = true
  timeout = 300

  # kind-specific: bind directly to host ports via hostPort
  set {
    name  = "controller.hostPort.enabled"
    value = "true"
  }

  set {
    name  = "controller.hostPort.ports.http"
    value = "80"
  }

  set {
    name  = "controller.hostPort.ports.https"
    value = "443"
  }

  # Run only on nodes labelled ingress-ready=true (set in kind config above)
  set {
    name  = "controller.nodeSelector.ingress-ready"
    value = "true"
  }

  # kind uses containerd — this toleration is needed for the control-plane node
  set {
    name  = "controller.tolerations[0].key"
    value = "node-role.kubernetes.io/control-plane"
  }

  set {
    name  = "controller.tolerations[0].operator"
    value = "Equal"
  }

  set {
    name  = "controller.tolerations[0].effect"
    value = "NoSchedule"
  }

  # Service type: NodePort is fine, but hostPort is what actually exposes the ports
  set {
    name  = "controller.service.type"
    value = "NodePort"
  }

  # Disable snippets (security best practice — avoid nginx.ingress.kubernetes.io/server-snippet)
  set {
    name  = "controller.allowSnippetAnnotations"
    value = "false"
  }

  depends_on = [kind_cluster.default]
}
```

---

### 3. Remove socat

Once the above is applied and working, remove socat from the server:

```bash
# Find and kill socat processes
sudo pkill socat

# Or find the PIDs first
ps aux | grep socat
kill <pid>
```

If socat was configured as a systemd service, disable it:
```bash
sudo systemctl stop k8s-port-forward
sudo systemctl disable k8s-port-forward
sudo rm /etc/systemd/system/k8s-port-forward.service
sudo systemctl daemon-reload
```

---

### 4. Verify after apply

```bash
# Ingress controller pod should be Running on the control-plane node
kubectl get pods -n ingress-nginx -o wide

# Service should exist (type NodePort is fine, hostPort does the real work)
kubectl get svc -n ingress-nginx

# Test HTTP directly on the host (no socat, no port-forward)
curl -v http://localhost/healthz

# Test from your Mac (with /etc/hosts entry pointing to server IP)
curl http://api.staging.blitz-pay.com/swagger-ui/index.html
```

---

## How It Works After This Change

```
Mac  →  Server:80  →  kind control-plane container:80 (extraPortMappings)
                              ↓
                     ingress-nginx pod (hostPort: 80)
                              ↓
                     blitz-pay service → blitz-pay pod
```

No socat. No kubectl port-forward. Ports 80/443 on the server go directly into the
ingress controller via kind's native port mapping.

---

## Checklist for the Agent

- [ ] Locate the Terraform resource that creates the kind cluster
- [ ] Add `extra_port_mappings` for ports 80 and 443 to the control-plane node
- [ ] Add `kubeadm_config_patches` to label the control-plane node `ingress-ready=true`
- [ ] Add or update the `helm_release` for ingress-nginx with `hostPort.enabled = true`
- [ ] Set `controller.nodeSelector.ingress-ready = true` on the Helm release
- [ ] Set `controller.tolerations` for the control-plane node taint
- [ ] Set `controller.allowSnippetAnnotations = false`
- [ ] Plan destruction and recreation of the cluster if `extraPortMappings` is new
- [ ] After `terraform apply`, verify ingress controller pod is running on control-plane node
- [ ] Confirm `curl http://localhost/health` returns 200 on the server
- [ ] Remove socat once confirmed working
