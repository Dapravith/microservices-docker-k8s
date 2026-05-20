#!/usr/bin/env bash
# Apply the k8s manifests in the correct order on the control plane.
set -euo pipefail

cd "$(dirname "$0")/../../k8s"

# If you pushed images to a registry, set IMAGE_REPO to rewrite the image prefix.
# Example: IMAGE_REPO=docker.io/yourdockerhubuser ./apply-k8s.sh
IMAGE_REPO="${IMAGE_REPO:-aupp}"

apply() {
  local file="$1"
  if [[ "$IMAGE_REPO" != "aupp" ]]; then
    sed "s|image: aupp/|image: ${IMAGE_REPO}/|g" "$file" | kubectl apply -f -
  else
    kubectl apply -f "$file"
  fi
}

apply 00-namespace.yaml
apply 01-secrets.yaml
apply 10-mongodb.yaml

echo "==> Waiting for MongoDB to be Ready..."
kubectl -n aupp rollout status deployment/mongodb --timeout=180s

apply 20-auth-service.yaml
apply 30-student-service.yaml
apply 40-teacher-service.yaml

echo "==> Waiting for backend services..."
kubectl -n aupp rollout status deployment/auth-service    --timeout=180s
kubectl -n aupp rollout status deployment/student-service --timeout=180s
kubectl -n aupp rollout status deployment/teacher-service --timeout=180s

apply 50-api-gateway.yaml
kubectl -n aupp rollout status deployment/api-gateway --timeout=180s

echo
echo "==> Deployments:"
kubectl -n aupp get deploy -o wide
echo
echo "==> Services:"
kubectl -n aupp get svc
echo
echo "==> Pods (per node):"
kubectl -n aupp get pods -o wide
echo
echo "==> Gateway NodePort URL (use any EC2 public IP):"
echo "   http://<EC2-public-IP>:30080"
