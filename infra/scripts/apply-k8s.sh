#!/usr/bin/env bash
# Apply Kubernetes manifests for the msp stack.
# Includes auto-configuration for single-node (control-plane only) setups.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT}/k8s"
NAMESPACE="msp"

echo "Using repo root: ${ROOT}"
echo "Using k8s dir:   ${K8S_DIR}"
echo

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl is not installed or not in PATH."
  exit 1
fi

if [[ ! -d "${K8S_DIR}" ]]; then
  echo "ERROR: k8s directory not found: ${K8S_DIR}"
  exit 1
fi

echo "Checking Kubernetes connection..."

if ! kubectl cluster-info >/dev/null 2>&1; then
  if [[ -f /etc/kubernetes/admin.conf ]]; then
    echo "kubectl unconfigured but /etc/kubernetes/admin.conf exists — fixing kubeconfig..."
    mkdir -p "$HOME/.kube"
    sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
    sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
    chmod 600 "$HOME/.kube/config"
  fi
fi

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "ERROR: kubectl cannot connect to the Kubernetes cluster."
  exit 1
fi

echo "Kubernetes connection OK."
echo

echo "Current nodes:"
kubectl get nodes -o wide
echo

# ==============================================================================
# SINGLE-NODE AUTO-CONFIGURE FIX
# ==============================================================================
NODE_COUNT=$(kubectl get nodes --no-headers | wc -l)
if [ "$NODE_COUNT" -eq 1 ]; then
  echo "==> Notice: Only 1 node detected in the cluster."
  echo "==> Automatically configuring the control-plane to accept application pods..."

  # Remove the NoSchedule taint from the control plane
  kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true

  # Apply a worker label just in case any manifests strictly require it
  NODE_NAME=$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')
  kubectl label node "$NODE_NAME" node-role.kubernetes.io/worker=worker --overwrite 2>/dev/null || true

  echo "==> Control-plane is now unlocked and acting as a worker node."
  echo
fi
# ==============================================================================

echo "Checking required manifest files..."

REQUIRED_FILES=(
  "00-namespace.yaml"
  "01-secrets.yaml"
  "mongodb.yaml"
  "mongodb-service.yaml"
  "auth.yaml"
  "auth-service.yaml"
  "registration.yaml"
  "registration-service.yaml"
  "student.yaml"
  "student-service.yaml"
  "teacher.yaml"
  "teacher-service.yaml"
  "api-gateway.yaml"
  "api-gateway-service.yaml"
)

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${K8S_DIR}/${file}" ]]; then
    echo "ERROR: Missing manifest file: ${K8S_DIR}/${file}"
    exit 1
  fi
done

echo "All manifest files found."
echo

# Clean up any stuck resources from the previous failed run
echo "Cleaning up any stuck pending resources..."
kubectl delete namespace "${NAMESPACE}" --ignore-not-found=true
echo

echo "Applying namespace and base resources..."
kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secrets.yaml"
echo

echo "Applying MongoDB..."
kubectl apply -f "${K8S_DIR}/mongodb.yaml"
kubectl apply -f "${K8S_DIR}/mongodb-service.yaml"

echo "Waiting for MongoDB rollout..."
kubectl -n "${NAMESPACE}" rollout status statefulset/mongodb --timeout=180s
echo

echo "Applying application services..."
kubectl apply -f "${K8S_DIR}/auth.yaml"
kubectl apply -f "${K8S_DIR}/auth-service.yaml"
kubectl apply -f "${K8S_DIR}/registration.yaml"
kubectl apply -f "${K8S_DIR}/registration-service.yaml"
kubectl apply -f "${K8S_DIR}/student.yaml"
kubectl apply -f "${K8S_DIR}/student-service.yaml"
kubectl apply -f "${K8S_DIR}/teacher.yaml"
kubectl apply -f "${K8S_DIR}/teacher-service.yaml"
kubectl apply -f "${K8S_DIR}/api-gateway.yaml"
kubectl apply -f "${K8S_DIR}/api-gateway-service.yaml"
echo

echo "Waiting for deployments..."

DEPLOYMENTS=(
  "authentication-service"
  "registration"
  "student-service"
  "teacher-service"
  "api-gateway"
)

for deploy in "${DEPLOYMENTS[@]}"; do
  echo "Checking rollout: ${deploy}"
  kubectl -n "${NAMESPACE}" rollout status "deploy/${deploy}" --timeout=180s
done

echo
echo "Deployment completed."
echo

echo "========== CLUSTER STATE =========="
kubectl get nodes -o wide
echo

echo "========== SERVICES =========="
kubectl -n "${NAMESPACE}" get services -o wide
echo

echo "========== DEPLOYMENTS =========="
kubectl -n "${NAMESPACE}" get deployments -o wide
echo

echo "========== STATEFULSETS =========="
kubectl -n "${NAMESPACE}" get statefulset -o wide
echo

echo "========== PODS =========="
kubectl -n "${NAMESPACE}" get pods -o wide
echo

echo "Gateway URL:"
echo "  http://<EC2-1_PUBLIC_IP>:30000"
echo

echo "Screenshot commands:"
echo "  kubectl -n ${NAMESPACE} get deployments -o wide"
echo "  kubectl -n ${NAMESPACE} get services -o wide"
echo "  kubectl -n ${NAMESPACE} get pods -o wide"