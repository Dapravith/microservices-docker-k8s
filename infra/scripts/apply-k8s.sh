#!/usr/bin/env bash
# Apply manifests in numeric order.
# Can be run from any directory.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT}/k8s"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl is not installed or not in PATH."
  exit 1
fi

if [[ ! -d "${K8S_DIR}" ]]; then
  echo "ERROR: k8s directory not found: ${K8S_DIR}"
  echo "Check that your repo structure is:"
  echo "  microservices-docker-k8s/k8s"
  echo "  microservices-docker-k8s/infra/scripts"
  exit 1
fi

echo "Using repo root: ${ROOT}"
echo "Using k8s dir:   ${K8S_DIR}"
echo

kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secrets.yaml"
kubectl apply -f "${K8S_DIR}/05-mongodb.yaml"

kubectl -n msp rollout status statefulset/mongo --timeout=180s

kubectl apply -f "${K8S_DIR}/10-login.yaml"
kubectl apply -f "${K8S_DIR}/15-registration.yaml"
kubectl apply -f "${K8S_DIR}/20-student.yaml"
kubectl apply -f "${K8S_DIR}/30-teacher.yaml"
kubectl apply -f "${K8S_DIR}/40-gateway.yaml"

kubectl -n msp rollout status deploy/login-service        --timeout=180s
kubectl -n msp rollout status deploy/registration-service --timeout=180s
kubectl -n msp rollout status deploy/student-service      --timeout=180s
kubectl -n msp rollout status deploy/teacher-service      --timeout=180s
kubectl -n msp rollout status deploy/api-gateway          --timeout=180s

echo
echo "Cluster state:"
kubectl -n msp get pods -o wide

echo
echo "Reach the gateway at:"
echo "  http://<EC2-1 public IP>:30000"