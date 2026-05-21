#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-aupp-local}"
TAG="${TAG:-1.0.0}"
IMAGE_REPO="${IMAGE_REPO:-dapravith99}"

"$ROOT_DIR/scripts/local/create-kind-cluster.sh"
"$ROOT_DIR/scripts/local/build-images.sh"

for image in \
  "$IMAGE_REPO/api-gateway:$TAG" \
  "$IMAGE_REPO/login-service:$TAG" \
  "$IMAGE_REPO/student-service:$TAG" \
  "$IMAGE_REPO/teacher-service:$TAG"; do
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done

kubectl apply -k "$ROOT_DIR/deploy/k8s"

# Force pods to pick up freshly rebuilt images: the :1.0.0 tag is fixed and
# imagePullPolicy is IfNotPresent, so `kubectl apply` alone leaves old pods running.
kubectl -n aupp rollout restart \
  deployment/api-gateway \
  deployment/login-service \
  deployment/student-service \
  deployment/teacher-service

kubectl -n aupp rollout status statefulset/mongodb --timeout=240s
kubectl -n aupp rollout status deployment/login-service --timeout=240s
kubectl -n aupp rollout status deployment/teacher-service --timeout=240s
kubectl -n aupp rollout status deployment/student-service --timeout=240s
kubectl -n aupp rollout status deployment/api-gateway --timeout=240s

echo
kubectl -n aupp get deploy,sts,svc,pvc -o wide
echo
kubectl -n aupp get pods -o wide
echo
echo "Gateway (NodePort, all endpoints): http://localhost:30080"
