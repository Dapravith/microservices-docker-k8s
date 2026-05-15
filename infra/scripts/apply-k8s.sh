#!/usr/bin/env bash
# Apply Kubernetes manifests for the msp stack.
# Run this on EC2-1 control-plane only, after:
# 1. kubeadm init completed
# 2. EC2-2 and EC2-3 joined the cluster
# 3. kubectl get nodes -o wide works
# 4. node labels are applied

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
  echo "Expected structure:"
  echo "  microservices-docker-k8s/k8s"
  echo "  microservices-docker-k8s/infra/scripts"
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
  echo
  echo "Most likely reasons:"
  echo "  1. You are running this script on EC2-2 or EC2-3 worker node."
  echo "  2. /etc/kubernetes/admin.conf is missing (kubeadm init not completed)."
  echo "  3. The API server pod is not yet running."
  echo
  echo "Run this on EC2-1 control-plane first:"
  echo "  sudo bash infra/scripts/init-control-plane.sh"
  echo
  echo "Current kubectl server:"
  kubectl config view --minify 2>/dev/null | grep server || echo "  (no context configured)"
  exit 1
fi

echo "Kubernetes connection OK."
echo

echo "Current Kubernetes server:"
kubectl config view --minify | grep server || true
echo

echo "Current nodes:"
kubectl get nodes -o wide
echo

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