#!/usr/bin/env bash
# Apply Kubernetes manifests for the msp stack.
# Includes auto-configuration, self-healing, and debug output.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT}/k8s"
NAMESPACE="msp"
DOCKERHUB_USER="${DOCKERHUB_USER:-dapravith99}"

echo "Using repo root: ${ROOT}"
echo "Using k8s dir:   ${K8S_DIR}"
echo

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl is not installed or not in PATH."
  exit 1
fi

echo "==> 0. Self-Healing: Extending root partition + filesystem (if EBS was resized)..."
ROOT_SRC="$(findmnt -n -o SOURCE / 2>/dev/null || true)"
ROOT_DEV="$(readlink -f "${ROOT_SRC:-/dev/root}" 2>/dev/null || echo "")"
if [[ -n "$ROOT_DEV" && -b "$ROOT_DEV" ]]; then
  PARENT_NAME="$(lsblk -n -o PKNAME "$ROOT_DEV" 2>/dev/null | head -n1 | tr -d ' ')"
  PARTNUM="$(echo "$ROOT_DEV" | grep -oE '[0-9]+$' || true)"
  if [[ -n "$PARENT_NAME" && -n "$PARTNUM" && -b "/dev/$PARENT_NAME" ]]; then
    sudo growpart "/dev/$PARENT_NAME" "$PARTNUM" 2>/dev/null || true
    sudo resize2fs "$ROOT_DEV" 2>/dev/null || true
    df -h / | tail -n1 | awk '{print "    Root now: " $4 " free (" $5 " used) on " $6}'
  else
    echo "    Skipped: could not resolve parent disk for $ROOT_DEV."
  fi
else
  echo "    Skipped: could not resolve root device (ROOT_SRC=${ROOT_SRC:-unset})."
fi
echo

echo "==> 1. Self-Healing: Deep cleaning disk space (Docker + Containerd + OS)..."
df -h / | tail -n1 | awk '{print "    Before: " $4 " free (" $5 " used) on " $6}'
# Container runtimes (NOTE: do NOT use `crictl rmp -af` here — it kills the
# kube-apiserver/etcd/controller-manager/scheduler static pods)
docker system prune -a --volumes -f >/dev/null 2>&1 || true
sudo crictl rmi --prune >/dev/null 2>&1 || true
# Logs (journal + rotated /var/log)
sudo journalctl --vacuum-size=50M >/dev/null 2>&1 || true
sudo find /var/log -type f \( -name "*.gz" -o -name "*.[0-9]" -o -name "*.old" \) -delete 2>/dev/null || true
sudo find /var/log -type f -name "*.log" -size +10M -exec truncate -s 0 {} \; 2>/dev/null || true
# APT + tmp (NOTE: never use `apt-get autoremove --purge` here — it can wipe
# /etc/kubernetes config when held packages have auto-installed dependencies)
sudo apt-get clean >/dev/null 2>&1 || true
sudo rm -rf /var/cache/apt/archives/*.deb /tmp/* /var/tmp/* 2>/dev/null || true
if command -v snap >/dev/null 2>&1; then
  LANG=C snap list --all 2>/dev/null | awk '/disabled/{print $1, $3}' \
    | while read -r s r; do sudo snap remove "$s" --revision="$r" >/dev/null 2>&1 || true; done
fi
df -h / | tail -n1 | awk '{print "    After:  " $4 " free (" $5 " used) on " $6}'
echo

echo "Checking Kubernetes connection (retrying for up to 60s)..."
for i in $(seq 1 12); do
  if kubectl cluster-info >/dev/null 2>&1; then
    echo "Kubernetes connection OK (after $((i*5))s)."
    break
  fi
  if [ "$i" -eq 12 ]; then
    echo "ERROR: kubectl cluster-info failing after 60s. Last error:"
    kubectl cluster-info 2>&1 | head -5
    exit 1
  fi
  sleep 5
done
echo

# ==============================================================================
# SINGLE-NODE AUTO-CONFIGURE FIX
# ==============================================================================
NODE_COUNT=$(kubectl get nodes --no-headers | wc -l)
if [ "$NODE_COUNT" -eq 1 ]; then
  echo "==> Notice: Only 1 node detected in the cluster."
  echo "==> Automatically configuring the control-plane to accept application pods..."

  # Remove taints (Control plane restriction AND old disk-pressure panics)
  kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
  kubectl taint nodes --all node.kubernetes.io/disk-pressure- 2>/dev/null || true

  NODE_NAME=$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')

  # Apply general worker label
  kubectl label node "$NODE_NAME" node-role.kubernetes.io/worker=worker --overwrite 2>/dev/null || true

  # CRITICAL FIX: Apply the specific label required by your mongodb.yaml
  kubectl label node "$NODE_NAME" db=mongo --overwrite 2>/dev/null || true

  echo "==> Control-plane unlocked and labeled with 'db=mongo'."
  echo
fi

# ==============================================================================
# AGGRESSIVE CLEANUP
# ==============================================================================
echo "==> 2. Self-Healing: Purging old stuck resources..."
kubectl delete statefulset mongodb -n "${NAMESPACE}" --ignore-not-found=true
kubectl delete pvc --all -n "${NAMESPACE}" --ignore-not-found=true
kubectl delete pv mongo-pv --ignore-not-found=true
echo "    Old storage and pods purged."
echo

# Ensure common local storage directories exist (prevents hostPath mount errors)
sudo mkdir -p /var/lib/mongo-data /data/db || true
sudo chmod -R 777 /var/lib/mongo-data /data/db || true

echo "Applying namespace and base resources..."
kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secrets.yaml"
echo

# Wait for kubelet to clear the disk-pressure taint (it manages this one itself —
# you can't just `kubectl taint -` it away; kubelet re-applies it until disk recovers)
echo "==> Waiting for node to clear disk-pressure taint (90s max)..."
for i in $(seq 1 18); do
  PRESSURED=$(kubectl get nodes -o jsonpath='{range .items[*]}{.spec.taints[?(@.key=="node.kubernetes.io/disk-pressure")].key}{"\n"}{end}' | grep -c disk-pressure || true)
  if [ "$PRESSURED" -eq 0 ]; then
    echo "    Disk-pressure clear after $((i*5))s."
    break
  fi
  if [ "$i" -eq 18 ]; then
    echo "    WARNING: disk-pressure taint still present after 90s."
    df -h /
    echo "    The root volume is too small. Resize the EBS volume to >=20G and re-run."
  fi
  sleep 5
done
echo

echo "Applying MongoDB..."
kubectl apply -f "${K8S_DIR}/mongodb.yaml"
kubectl apply -f "${K8S_DIR}/mongodb-service.yaml"

echo "Waiting for MongoDB rollout (120s max)..."
# ==============================================================================
# AUTO-DEBUGGING BLOCK
# ==============================================================================
if ! kubectl -n "${NAMESPACE}" rollout status statefulset/mongodb --timeout=120s; then
  echo
  echo "========================================================================"
  echo "❌ ERROR: MongoDB rollout failed or timed out. Gathering debug data..."
  echo "========================================================================"
  echo "--- DISK SPACE CHECK ---"
  df -h /
  echo
  echo "--- POD STATUS ---"
  kubectl -n "${NAMESPACE}" get pods
  echo
  echo "--- EXACT ERROR LOG ---"
  POD_NAME=$(kubectl -n "${NAMESPACE}" get pods -l app=mongodb -o jsonpath="{.items[0].metadata.name}" 2>/dev/null || echo "")
  if [[ -n "$POD_NAME" ]]; then
      kubectl -n "${NAMESPACE}" describe pod "$POD_NAME" | tail -n 20
  else
      echo "MongoDB Pod not found!"
  fi
  echo "========================================================================"
  exit 1
fi
# ==============================================================================

echo "Applying application services (substituting DOCKERHUB_USER=${DOCKERHUB_USER})..."
APP_MANIFESTS=(auth registration student teacher api-gateway)
for svc in "${APP_MANIFESTS[@]}"; do
  sed "s|DOCKERHUB_USER|${DOCKERHUB_USER}|g" "${K8S_DIR}/${svc}.yaml" | kubectl apply -f -
  kubectl apply -f "${K8S_DIR}/${svc}-service.yaml"
done
echo

# Single-node only: strip nodeAffinity so app pods can schedule on the lone node
# (manifests pin role=frontend/student/teacher, which can't all be satisfied by one node)
if [ "$NODE_COUNT" -eq 1 ]; then
  echo "==> Single-node mode: removing nodeAffinity from app deployments so they can schedule..."
  for deploy in authentication-service registration student-service teacher-service api-gateway; do
    kubectl -n "${NAMESPACE}" patch deploy "$deploy" --type=json \
      -p='[{"op":"remove","path":"/spec/template/spec/affinity"}]' 2>/dev/null || true
  done
fi

echo "Waiting for deployments..."
DEPLOYMENTS=("authentication-service" "registration" "student-service" "teacher-service" "api-gateway")
for deploy in "${DEPLOYMENTS[@]}"; do
  echo "Checking rollout: ${deploy}"
  kubectl -n "${NAMESPACE}" rollout status "deploy/${deploy}" --timeout=120s
done

echo
echo "✅ Deployment completed successfully."
echo "Gateway URL: http://<EC2-1_PUBLIC_IP>:30000"