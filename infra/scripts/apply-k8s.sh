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

echo "==> 1. Self-Healing: Deep cleaning disk space (Docker + Containerd + OS)..."
# Clean Docker
docker system prune -a --volumes -f >/dev/null 2>&1 || true
# Clean Containerd (Kubernetes runtime)
sudo crictl rmi --prune >/dev/null 2>&1 || true
# Clean Ubuntu Apt cache and old system logs
sudo apt-get clean >/dev/null 2>&1 || true
sudo journalctl --vacuum-time=1h >/dev/null 2>&1 || true
echo "    Disk space completely cleared."
echo

echo "Checking Kubernetes connection..."
if ! kubectl cluster-info >/dev/null 2>&1; then
  exit 1
fi
echo "Kubernetes connection OK."
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